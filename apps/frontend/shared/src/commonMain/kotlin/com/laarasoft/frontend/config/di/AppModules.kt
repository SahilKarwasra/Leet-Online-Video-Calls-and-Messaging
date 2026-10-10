package com.laarasoft.frontend.config.di

import com.laarasoft.frontend.presentation.HomeViewModel
import com.laarasoft.frontend.presentation.MeetViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val platformModule: Module

val appModules = module {
    includes(platformModule)
    viewModelOf(::HomeViewModel)
    viewModelOf(::MeetViewModel)
}