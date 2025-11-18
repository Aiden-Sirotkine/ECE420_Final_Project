//
// Created by daran on 1/12/2017 to be used in ECE420 Sp17 for the first time.
// Modified by dwang49 on 1/1/2018 to adapt to Android 7.0 and Shield Tablet updates.
//

#include "ece420_main.h"
#include "ece420_lib.h"
#include "kiss_fft/kiss_fft.h"

// JNI Function
extern "C" {
JNIEXPORT float JNICALL
Java_com_ece420_lab4_MainActivity_getFreqUpdate(JNIEnv *env, jclass);
}

// Student Variables
#define F_S 48000
#define FRAME_SIZE 1024
#define VOICED_THRESHOLD 1000000000  // Find your own threshold
float lastFreqDetected = -1;

void ece420ProcessFrame(sample_buf *dataBuf) {
    // Keep in mind, we only have 20ms to process each buffer!
    struct timeval start;
    struct timeval end;
    gettimeofday(&start, NULL);

    // Data is encoded in signed PCM-16, little-endian, mono
    float bufferIn[FRAME_SIZE];
    for (int i = 0; i < FRAME_SIZE; i++) {
        int16_t val = ((uint16_t) dataBuf->buf_[2 * i]) | (((uint16_t) dataBuf->buf_[2 * i + 1]) << 8);
        bufferIn[i] = (float) val;
    }

    // ********************** PITCH DETECTION ************************ //
    // In this section, you will be computing the autocorrelation of bufferIn
    // and picking the delay corresponding to the best match. Naively computing the
    // autocorrelation in the time domain is an O(N^2) operation and will not fit
    // in your timing window.
    //
    // First, you will have to detect whether or not a signal is voiced.
    // We will implement a simple voiced/unvoiced detector by thresholding
    // the power of the signal.
    //
    // Next, you will have to compute autocorrelation in its O(N logN) form.
    // Autocorrelation using the frequency domain is given as:
    //
    //  autoc = ifft(fft(x) * conj(fft(x)))
    //
    // where the fft multiplication is element-wise.
    //
    // You will then have to find the index corresponding to the maximum
    // of the autocorrelation. Consider that the signal is a maximum at idx = 0,
    // where there is zero delay and the signal matches perfectly.
    //
    // Finally, write the variable "lastFreqDetected" on completion. If voiced,
    // write your determined frequency. If unvoiced, write -1.
    // ********************* START YOUR CODE HERE *********************** //

    double energy = 0;

    for (double item : bufferIn) {
        energy += item * item;
    }

    if (energy < VOICED_THRESHOLD){
        lastFreqDetected = -1;
        return;
    }

//   Now find the autocorrelation using a Fourier Transform

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    // Initialize KISS FFT
    int nfft = FRAME_SIZE;
    kiss_fft_cfg cfg = kiss_fft_alloc(nfft, 0, 0, 0);

    // Allocate input and output arrays for complex numbers
    kiss_fft_cpx* cx_in = new kiss_fft_cpx[nfft];
    kiss_fft_cpx* cx_out = new kiss_fft_cpx[nfft];

    // filling the real and imaginary bits????
    for (size_t i = 0; i < FRAME_SIZE; i++) {
        cx_in[i].r = bufferIn[i];
        cx_in[i].i = 0.0;
    }

//   Now I perform the actual FFT, which I think involves just using a library.


    // Perform the FFT
    kiss_fft(cfg, cx_in, cx_out);

    double square[FRAME_SIZE] = {};

    // take the square before doing the inverse transform
    for (size_t k = 0; k < FRAME_SIZE; k++) { // Only need first half + DC
        square[k] = cx_out[k].r * cx_out[k].r + cx_out[k].i * cx_out[k].i;
    }

    /////////////// INVERSE FOURIER TRANSFORM TIME

    kiss_fft_cfg cfg_inverse = kiss_fft_alloc(nfft, 1, 0, 0); // 1 indicates inverse FFT

    kiss_fft_cpx* cx_in_inverse = new kiss_fft_cpx[nfft];
    kiss_fft_cpx* cx_out_inverse = new kiss_fft_cpx[nfft];

    // filling the real and imaginary bits????
    for (size_t i = 0; i < FRAME_SIZE; i++) {
        cx_in[i].r = square[i];
        cx_in[i].i = 0.0;
    }

    kiss_fft(cfg, cx_in, cx_out);

//
    float auto_cor[FRAME_SIZE] = {};

    for (size_t i = 0; i < FRAME_SIZE; i++) {
        auto_cor[i] = cx_out[i].r;
    }

    int nicest_frequency = findMaxArrayIdx(auto_cor, 50, FRAME_SIZE-50);


    lastFreqDetected = 2 * F_S/nicest_frequency;


    kiss_fft_free(cfg);
    delete[] cx_in;
    delete[] cx_out;

    kiss_fft_free(cfg_inverse);
    delete[] cx_in_inverse;
    delete[] cx_out_inverse;







///////////////////////////////////////////////////////////////////////////////////////////////////////



    // ********************* END YOUR CODE HERE ************************* //
    gettimeofday(&end, NULL);
    LOGD("Time delay: %ld us",  ((end.tv_sec * 1000000 + end.tv_usec) - (start.tv_sec * 1000000 + start.tv_usec)));
}

JNIEXPORT float JNICALL
Java_com_ece420_lab4_MainActivity_getFreqUpdate(JNIEnv *env, jclass) {
    return lastFreqDetected;
}