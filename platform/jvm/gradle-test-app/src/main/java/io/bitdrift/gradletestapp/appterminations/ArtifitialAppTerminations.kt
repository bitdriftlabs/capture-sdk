package io.bitdrift.gradletestapp.appterminations

import io.bitdrift.capture.Capture
import java.util.concurrent.Executors
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

object ArtifitialAppTerminations {

    fun forceAnr() {
        Thread.sleep(15000)
    }

    fun forceRegularCrash() {
        throw RuntimeException("Forced unhandled exception")
    }

    fun forceTooManyThreadsCrash() {
        Observable.interval(0, 100, TimeUnit.MICROSECONDS)
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .subscribe {
                Thread.sleep(200)
            }
    }
}