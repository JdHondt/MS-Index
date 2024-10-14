package net.jelter.utils;

public class Tuple2<A, B> {

    public final A _1;
    public final B _2;

    public Tuple2(A _1, B _2) {
        this._1 = _1;
        this._2 = _2;
    }

    public static <A, B> Tuple2<A, B> of(A _1, B _2) {
        return new Tuple2<>(_1, _2);
    }

    @Override
    public String toString() {
        return "(" + _1 + ", " + _2 + ")";
    }

    public A _1() {
        return _1;
    }

    public B _2() {
        return _2;
    }

}
