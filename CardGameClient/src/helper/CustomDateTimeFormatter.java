package helper;

public class CustomDateTimeFormatter {
    public static String secondsToMinutes(int s) {
        int m = s / 60; int r = s % 60; 
        return (m < 10 ? "0"+m : ""+m) + ":" + (r < 10 ? "0"+r : ""+r);
    }
}