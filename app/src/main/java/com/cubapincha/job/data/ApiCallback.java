package com.cubapincha.job.data;

/** Callback entregado siempre en el hilo principal. */
public interface ApiCallback<T> {
    void onResult(ApiResult<T> result);
}
