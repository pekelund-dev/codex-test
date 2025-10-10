package dev.pekelund.pklnd.web;

public record UserProfile(boolean authenticated, String displayName, String initials, String imageUrl) {

    public static UserProfile anonymous() {
        return new UserProfile(false, null, null, null);
    }
}
