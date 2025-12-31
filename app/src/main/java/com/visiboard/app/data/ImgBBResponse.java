package com.visiboard.app.data;

import com.google.gson.annotations.SerializedName;

public class ImgBBResponse {
    @SerializedName("data")
    private Data data;
    
    @SerializedName("success")
    private boolean success;
    
    @SerializedName("status")
    private int status;

    public Data getData() {
        return data;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getStatus() {
        return status;
    }

    public static class Data {
        @SerializedName("url")
        private String url;
        
        @SerializedName("display_url")
        private String displayUrl;
        
        @SerializedName("delete_url")
        private String deleteUrl;

        public String getUrl() {
            return url;
        }
        
        public String getDisplayUrl() {
            return displayUrl;
        }
        
        public String getDeleteUrl() {
            return deleteUrl;
        }
    }
}
