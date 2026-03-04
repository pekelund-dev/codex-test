package dev.pekelund.pklnd.web;

public record UserProfile(boolean authenticated, String displayName, String initials, String imageUrl, boolean demoMode) {

    public static UserProfile anonymous() {
        return new UserProfile(false, null, null, null, false);
    }

    public static UserProfile demo(String displayName, String initials) {
        return new UserProfile(true, displayName, initials, null, true);
    }
}
