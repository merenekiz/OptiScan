package com.optiscan.di

import android.content.Context
import com.optiscan.data.OptiScanDatabase
import com.optiscan.data.dao.ExamDao
import com.optiscan.data.dao.ScanSessionDao
import com.optiscan.data.dao.StudentResultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): OptiScanDatabase =
        OptiScanDatabase.getInstance(context)

    @Provides
    fun provideExamDao(db: OptiScanDatabase): ExamDao = db.examDao()

    @Provides
    fun provideStudentResultDao(db: OptiScanDatabase): StudentResultDao = db.studentResultDao()

    @Provides
    fun provideScanSessionDao(db: OptiScanDatabase): ScanSessionDao = db.scanSessionDao()
}
