package com.roni.blocks.core.di

import com.roni.blocks.core.steps.AndroidStepSensor
import com.roni.blocks.core.steps.StepSensor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StepModule {

    @Provides
    @Singleton
    fun stepSensor(sensor: AndroidStepSensor): StepSensor = sensor
}
