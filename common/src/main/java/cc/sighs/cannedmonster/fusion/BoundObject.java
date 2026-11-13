package cc.sighs.cannedmonster.fusion;

public interface BoundObject {
    Object cannedmonster$body();

    void cannedmonster$body(Object body);

    default Object cannedmonster$session() {
        return null;
    }

    default void cannedmonster$session(Object session) {
    }
}
