import CoreImage
import Foundation
import ReplayKit

// ReplayKit Broadcast Upload Extension: iOS hands it every frame of the screen
// while the user broadcasts. It runs in its own process (with a ~50 MB memory
// limit), so it only shrinks and JPEG-encodes frames and sends them to the app
// over a Unix socket in the shared App Group folder. The app does the WebRTC part.
// See iosApp/iosApp/ScreenShareBroadcast.swift for the frame format.
final class SampleHandler: RPBroadcastSampleHandler {
    private static let stopNotification = "com.laarasoft.leet.screenshare.stop"
    private static let maxSide: CGFloat = 1280
    private static let frameInterval: CFTimeInterval = 1.0 / 15

    private let sendQueue = DispatchQueue(label: "leet.screenshare.send")
    private let ciContext = CIContext(options: [.useSoftwareRenderer: false])
    private var socket: Int32 = -1
    private var lastFrameTime: CFTimeInterval = 0
    private var sending = false

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        guard let path = socketPath(), connect(to: path) else {
            finish("Open the meeting in Leet first, then start sharing from there.")
            return
        }
        observeStopRequests()
    }

    override func broadcastFinished() {
        sendQueue.async {
            if self.socket >= 0 {
                close(self.socket)
                self.socket = -1
            }
        }
    }

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        guard sampleBufferType == .video,
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        // Drop frames while the previous one is still being sent, and cap the rate.
        let now = CACurrentMediaTime()
        guard !sending, now - lastFrameTime >= Self.frameInterval else { return }
        lastFrameTime = now
        sending = true

        let rotation = Self.rotation(of: sampleBuffer)
        let timestamp = UInt64(CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) * 1_000_000_000)
        let image = CIImage(cvPixelBuffer: pixelBuffer)

        sendQueue.async {
            defer { self.sending = false }
            guard let jpeg = self.encode(image) else { return }
            var frame = Data()
            frame.append(bigEndian: UInt32(jpeg.count))
            frame.append(bigEndian: UInt32(rotation))
            frame.append(bigEndian: timestamp)
            frame.append(jpeg)
            if !self.write(frame) {
                self.finish("Screen sharing ended because the meeting closed.")
            }
        }
    }

    // MARK: - Frames

    private func encode(_ image: CIImage) -> Data? {
        let longSide = max(image.extent.width, image.extent.height)
        let scale = min(1, Self.maxSide / longSide)
        let scaled = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        return ciContext.jpegRepresentation(
            of: scaled,
            colorSpace: CGColorSpaceCreateDeviceRGB(),
            options: [kCGImageDestinationLossyCompressionQuality as CIImageRepresentationOption: 0.6]
        )
    }

    // Degrees the frame must be rotated clockwise to be upright.
    private static func rotation(of sampleBuffer: CMSampleBuffer) -> Int {
        guard let value = CMGetAttachment(sampleBuffer, key: RPVideoSampleOrientationKey as CFString, attachmentModeOut: nil) as? NSNumber,
              let orientation = CGImagePropertyOrientation(rawValue: value.uint32Value)
        else { return 0 }
        switch orientation {
        case .left, .leftMirrored: return 90
        case .down, .downMirrored: return 180
        case .right, .rightMirrored: return 270
        default: return 0
        }
    }

    // MARK: - Socket

    private func socketPath() -> String? {
        guard let appGroup = Bundle.main.object(forInfoDictionaryKey: "LeetAppGroup") as? String,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
        else { return nil }
        return container.appendingPathComponent("screenshare.sock").path
    }

    private func connect(to path: String) -> Bool {
        var address = sockaddr_un()
        address.sun_family = sa_family_t(AF_UNIX)
        let bytes = Array(path.utf8)
        guard bytes.count < MemoryLayout.size(ofValue: address.sun_path) else { return false }
        withUnsafeMutableBytes(of: &address.sun_path) { raw in
            raw.copyBytes(from: bytes)
            raw[bytes.count] = 0
        }
        let fd = Darwin.socket(AF_UNIX, SOCK_STREAM, 0)
        guard fd >= 0 else { return false }
        // Writing to a socket the app closed must fail, not kill the extension.
        var noSigPipe: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &noSigPipe, socklen_t(MemoryLayout<Int32>.size))
        let connected = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.connect(fd, $0, socklen_t(MemoryLayout<sockaddr_un>.size))
            }
        }
        guard connected == 0 else {
            close(fd)
            return false
        }
        socket = fd
        return true
    }

    private func write(_ data: Data) -> Bool {
        guard socket >= 0 else { return false }
        return data.withUnsafeBytes { raw in
            var offset = 0
            while offset < raw.count {
                let n = Darwin.write(socket, raw.baseAddress! + offset, raw.count - offset)
                if n <= 0 { return false }
                offset += n
            }
            return true
        }
    }

    // MARK: - Stopping

    private func observeStopRequests() {
        let observer = Unmanaged.passUnretained(self).toOpaque()
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            observer,
            { _, observer, _, _, _ in
                guard let observer else { return }
                let handler = Unmanaged<SampleHandler>.fromOpaque(observer).takeUnretainedValue()
                handler.finish("You stopped presenting.")
            },
            Self.stopNotification as CFString,
            nil,
            .deliverImmediately
        )
    }

    private func finish(_ message: String) {
        CFNotificationCenterRemoveEveryObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            Unmanaged.passUnretained(self).toOpaque()
        )
        // ReplayKit shows this message in the "Broadcast stopped" alert.
        finishBroadcastWithError(NSError(
            domain: "com.laarasoft.leet.screenshare",
            code: 0,
            userInfo: [NSLocalizedDescriptionKey: message]
        ))
    }
}

private extension Data {
    mutating func append<T: FixedWidthInteger>(bigEndian value: T) {
        var big = value.bigEndian
        Swift.withUnsafeBytes(of: &big) { append(contentsOf: $0) }
    }
}
