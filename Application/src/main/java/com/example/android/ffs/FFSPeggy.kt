package com.example.android.ffs

import java.math.BigInteger
import java.security.SecureRandom

class FFSPeggy(
    private val n: BigInteger,
    private val s: ArrayList<BigInteger>,
    private val l: Int,                     // Security parameter
    private val k: Int,                     // Security parameter
    seed: ByteArray
) {
    private val random: SecureRandom = SecureRandom(seed)

    private val v: ArrayList<BigInteger> = ArrayList(k)
    private var r: BigInteger = BigInteger(l, random)
    private val sign: Boolean = random.nextBoolean()

    // Helper values
    private val two = BigInteger("2")
    private val negativeOne = BigInteger("-1")
    private val negativeTwo = two.times(negativeOne)

    // State
    public var currentStep = 0

    fun getV(): ArrayList<BigInteger> {
        currentStep = 1

        v.clear()
        v.add(n)
        for (i in 0 until k) {
            // v_i = s_i^(-2) mod n
            v.add(s[i].modPow(negativeTwo, n))
        }

        return v
    }

    fun getX(): BigInteger {
        currentStep = 2
        r = BigInteger(l, random)

        // x = (s * r) mod n
        return if (sign) {
            r.modPow(two, n)
        } else {
            r.pow(2).times(negativeOne).mod(n)
        }
    }

    fun getY(a: ArrayList<Boolean>): BigInteger {
        currentStep = 3

        // y = r * s_1 ^ a_1 * ... * s_k ^ a_k
        var y = r.mod(n)
        for (i in 0 until k) {
            if (a[i]) {
                y = y.times(s[i]).mod(n)
            }
        }

        return y
    }

    fun nextStep() {
        currentStep += 1
    }
}