package com.visiboard.app.data;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface ImgBBService {
    @Multipart
    @POST("1/upload")
    Call<ImgBBResponse> uploadImage(
        @Query("key") String apiKey,
        @Part MultipartBody.Part image
    );
}
