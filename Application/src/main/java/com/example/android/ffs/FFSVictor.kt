package com.example.android.ffs

import java.math.BigInteger
import java.security.SecureRandom

class FFSVictor(
        private var n: BigInteger,
        private val k: Int,         // Security parameter
        seed: ByteArray
) {
    private val random: SecureRandom = SecureRandom(seed)

    private val a: ArrayList<Boolean> = ArrayList(k)

    // Recieved values
    private var v: ArrayList<BigInteger> = ArrayList(0)
    private var x: BigInteger = BigInteger("0")

    // Helper values
    private val two = BigInteger("2")
    private val negativeOne = BigInteger("-1")
    private val negativeTwo = two.times(negativeOne)

    // State
    public var currentStep = 0
    var gotV = false

    fun receiveV(v: ArrayList<BigInteger>) {
        currentStep = 1

        if (!gotV) {
            this.n = v[0]
            v.removeAt(0)
            this.v = v
            gotV = true
        }
    }

    fun getA(x: BigInteger): ArrayList<Boolean> {
        currentStep = 2

        this.x = x

        a.clear()
        for (i in 0 until k) {
            a.add(random.nextBoolean())
        }

        return a
    }

    fun check(y: BigInteger): Boolean {
        currentStep = 3

        // y^2 = |x * v_1 ^ a_1 * ... * v_k ^ a_k|
        var res = y.modPow(two, n)

        for (i in 0 until k) {
            if (a[i]) {
                res = res.times(v[i]).mod(n)
            }
        }

        val resNegative = res.times(negativeOne).mod(n)

        return x == res || x == resNegative
    }

    fun nextStep() {
        currentStep += 1
    }
}