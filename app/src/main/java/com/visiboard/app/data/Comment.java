package com.visiboard.app.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Comment {
    public String id;
    public String userId;
    public String userName;
    public String text;
    public long timestamp;
    public List<Map<String, Object>> mentions;  // For Firestore compatibility

    public Comment() {
    }

    public Comment(String id, String userId, String userName, String text, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.text = text;
        this.timestamp = timestamp;
        this.mentions = new ArrayList<>();
    }

    /**
     * Parse the raw mentions list into Mention objects
     */
    public List<Mention> getMentionsParsed() {
        List<Mention> result = new ArrayList<>();
        if (mentions == null) return result;
        
        for (Map<String, Object> mentionMap : mentions) {
            try {
                String userId = (String) mentionMap.get("userId");
                String userName = (String) mentionMap.get("userName");
                int startIndex = mentionMap.get("startIndex") != null 
                    ? ((Number) mentionMap.get("startIndex")).intValue() : 0;
                int endIndex = mentionMap.get("endIndex") != null 
                    ? ((Number) mentionMap.get("endIndex")).intValue() : 0;
                Boolean isFollowing = (Boolean) mentionMap.get("isFollowingMention");
                
                Mention mention = new Mention(userId, userName, startIndex, endIndex, 
                    isFollowing != null && isFollowing);
                result.add(mention);
            } catch (Exception e) {
                // Skip malformed mention
            }
        }
        return result;
    }

    /**
     * Set mentions from Mention objects (converts to Firestore-compatible format)
     */
    public void setMentionsFromList(List<Mention> mentionList) {
        if (mentionList == null) {
            this.mentions = new ArrayList<>();
            return;
        }
        
        this.mentions = new ArrayList<>();
        for (Mention mention : mentionList) {
            java.util.HashMap<String, Object> map = new java.util.HashMap<>();
            map.put("userId", mention.getUserId());
            map.put("userName", mention.getUserName());
            map.put("startIndex", mention.getStartIndex());
            map.put("endIndex", mention.getEndIndex());
            map.put("isFollowingMention", mention.isFollowingMention());
            this.mentions.add(map);
        }
    }
}

