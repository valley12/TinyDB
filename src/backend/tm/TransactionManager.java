package backend.tm;

public interface TransactionManager {
    long begin();
    void commit(long xid);
    void abort(long xid);
    boolean isActive(long xid);
    boolean isCommited(long xid);
    boolean isAborted(long xid);
    void close();
}
