package com.optiscan.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for application-level singletons.
 *
 * Processing classes (OmrProcessor, BubbleDetector, PerspectiveTransformer,
 * OcrProcessor, QrScanner, CameraManager, GradingEngine, ExcelExporter,
 * FormPdfGenerator) use @Inject constructor + @Singleton, so Hilt resolves
 * them automatically. Do NOT duplicate @Provides bindings for them here.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
