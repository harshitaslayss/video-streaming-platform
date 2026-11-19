package com.netflix.contentservice.model;

/*
* gives the video uploading lifecycle
* PENDING-> UPLOADED-> ENCODING -> ENCODED -> READY
*                               -> FAILED
 * */
public enum VideoStatus {
    PENDING, //movie added but not yet uploaded to s3
    UPLOADED, //raw video added to s3
    ENCODING, // FFmpeg video encoding underway
    ENCODED, //encoding complete
    READY, //HLS playlist is ready and can be streamed
    FAILED //encoding failed
}
