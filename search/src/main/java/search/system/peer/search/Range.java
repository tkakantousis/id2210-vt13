package search.system.peer.search;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA. User: kazarindn Date: 4/22/13 Time: 4:15 PM
 */
public class Range implements Serializable{

    private  int lower;
    private  int upper;

    public Range(int lower, int upper) {
        this.lower = lower;
        this.upper = upper;
    }

    public int getLower() {
        return lower;
    }

    public int getUpper() {
        return upper;
    }

    public void setLower(int lower) {
        this.lower = lower;
    }

    public void setUpper(int upper) {
        this.upper = upper;
    }

    @Override
    public String toString() {
        return "Range{" + "lower=" + lower + ", upper=" + upper + '}';
    }
}
