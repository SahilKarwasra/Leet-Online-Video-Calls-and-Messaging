import CoreImage
import CoreVideo
import Foundation
import ReplayKit
import Shared
import UIKit

// The app side of iOS screen sharing (see IosScreenShare.kt). The broadcast
// extension (LeetScreenShare) connects to a Unix socket in the App Group folder
// and sends JPEG frames; they are decoded here and handed to Kotlin, which
// feeds them into WebRTC.
//
// Frame format, all integers big-endian:
//   UInt32 JPEG length | UInt32 rotation in degrees | UInt64 timestamp ns | JPEG bytes
final class ScreenShareBroadcast: NSObject, IosScreenBroadcast {
    static let stopNotification = "com.laarasoft.leet.screenshare.stop"

    private let appGroup = Bundle.main.object(forInfoDictionaryKey: "LeetAppGroup") as? String
    private let extensionId = Bundle.main.object(forInfoDictionaryKey: "LeetScreenShareExtension") as? String
    private let queue = DispatchQueue(label: "leet.screenshare.socket")
    private let ciContext = CIContext()

    private var listener: IosScreenFrameListener?
    private var serverSocket: Int32 = -1

    var isAvailable: Bool {
        #if targetEnvironment(simulator)
        return false // ReplayKit broadcasts only run on a real device.
        #else
        return socketPath != nil && !(extensionId ?? "").isEmpty
        #endif
    }

    private var socketPath: String? {
        guard let appGroup, !appGroup.isEmpty,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)
        else { return nil }
        return container.appendingPathComponent("screenshare.sock").path
    }

    func startListening(listener: IosScreenFrameListener) {
        queue.async {
            self.listener = listener
            if self.serverSocket < 0 { self.openServer() }
        }
    }

    func stopListening() {
        queue.async {
            self.listener = nil
            if self.serverSocket >= 0 {
                close(self.serverSocket)
                self.serverSocket = -1
            }
        }
    }

    func showPicker() {
        DispatchQueue.main.async {
            let picker = RPSystemBroadcastPickerView(frame: CGRect(x: 0, y: 0, width: 44, height: 44))
            picker.preferredExtension = self.extensionId
            picker.showsMicrophoneButton = false
            // The picker only exposes a button; pressing it opens the system sheet.
            for case let button as UIButton in picker.subviews {
                button.sendActions(for: .allEvents)
            }
        }
    }

    func requestStop() {
        CFNotificationCenterPostNotification(
            CFNotificationCenterGetDarwinNotifyCenter(),
            CFNotificationName(Self.stopNotification as CFString),
            nil, nil, true
        )
    }

    // MARK: - Socket server (runs on `queue` and a reader thread)

    private func openServer() {
        guard let path = socketPath else { return }
        unlink(path)
        let fd = socket(AF_UNIX, SOCK_STREAM, 0)
        guard fd >= 0, var address = unixAddress(path) else { return }
        let bound = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                bind(fd, $0, socklen_t(MemoryLayout<sockaddr_un>.size))
            }
        }
        guard bound == 0, listen(fd, 1) == 0 else {
            close(fd)
            return
        }
        serverSocket = fd
        Thread.detachNewThread { [weak self] in self?.acceptLoop(fd) }
    }

    private func acceptLoop(_ server: Int32) {
        while true {
            let client = accept(server, nil, nil)
            if client < 0 { return } // server socket closed
            queue.sync { listener }?.onStarted()
            readFrames(client)
            close(client)
            queue.sync { listener }?.onEnded()
        }
    }

    private func readFrames(_ fd: Int32) {
        var header = [UInt8](repeating: 0, count: 16)
        while readFully(fd, &header, 16) {
            let length = Int(header[0..<4].reduce(0) { $0 << 8 | UInt32($1) })
            let rotation = Int32(header[4..<8].reduce(0) { $0 << 8 | UInt32($1) })
            let timestamp = Int64(bitPattern: header[8..<16].reduce(0) { $0 << 8 | UInt64($1) })
            guard length > 0, length < 20_000_000 else { return }
            var jpeg = [UInt8](repeating: 0, count: length)
            guard readFully(fd, &jpeg, length) else { return }
            guard let listener = queue.sync(execute: { listener }),
                  let pixelBuffer = decode(Data(jpeg)) else { continue }
            listener.onFrame(
                pixelBuffer: Unmanaged.passUnretained(pixelBuffer).toOpaque(),
                rotation: rotation,
                timestampNs: timestamp
            )
        }
    }

    private func decode(_ jpeg: Data) -> CVPixelBuffer? {
        guard let image = CIImage(data: jpeg) else { return nil }
        var buffer: CVPixelBuffer?
        let attributes = [kCVPixelBufferIOSurfacePropertiesKey: [:]] as CFDictionary
        CVPixelBufferCreate(
            kCFAllocatorDefault,
            Int(image.extent.width), Int(image.extent.height),
            kCVPixelFormatType_32BGRA, attributes, &buffer
        )
        guard let buffer else { return nil }
        ciContext.render(image, to: buffer)
        return buffer
    }

    private func readFully(_ fd: Int32, _ buffer: inout [UInt8], _ count: Int) -> Bool {
        var offset = 0
        while offset < count {
            let n = buffer.withUnsafeMutableBytes { read(fd, $0.baseAddress! + offset, count - offset) }
            if n <= 0 { return false }
            offset += n
        }
        return true
    }
}

func unixAddress(_ path: String) -> sockaddr_un? {
    var address = sockaddr_un()
    address.sun_family = sa_family_t(AF_UNIX)
    let bytes = Array(path.utf8)
    guard bytes.count < MemoryLayout.size(ofValue: address.sun_path) else { return nil }
    withUnsafeMutableBytes(of: &address.sun_path) { raw in
        raw.copyBytes(from: bytes)
        raw[bytes.count] = 0
    }
    return address
}
