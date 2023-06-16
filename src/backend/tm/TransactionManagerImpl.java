package backend.tm;

import backend.utils.Panic;
import backend.utils.Parser;
import common.Error;


import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransactionManagerImpl implements TransactionManager{
    // XID文件头长度，8字节
    static final int LEN_XID_HEADER_LENGTH = 8;
    //事务占用长度，1字节?
    private static final int XID_FIELD_SIZE = 1;
    //事务状态，active(0), committed(1), aborted(2)
    private static final byte FIELD_TRAN_ACTIVE = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED = 2;

    //超级事务，永远是提交的状态
    public static final long SUPER_XID = 0;

    static final String XID_SUFFIX = ".xid";

    private RandomAccessFile file;
    private FileChannel fc;

    //事务ID计数
    private long xidCounter;
    private Lock counterLock;

    // 构造方法
    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc){
        this.file = raf;
        this.fc = fc;
        counterLock = new ReentrantLock();
        checkXIDCounter();
    }
    //对XID文件进行校验，确保合法的XID文件
    private void checkXIDCounter(){
        long fileLen = 0;
        try{
            fileLen = file.length();
        }catch (IOException e1){
            Panic.panic(Error.BadXIDFileException);
        }
        if(fileLen < LEN_XID_HEADER_LENGTH){
            Panic.panic(Error.BadXIDFileException);
        }

        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);
        try{
            fc.position(0);
            fc.read(buf);
        }catch(IOException e){
            Panic.panic(e);
        }
        this.xidCounter = Parser.parserLong(buf.array());
        long end = getXidPosition(this.xidCounter + 1);
        if(end != fileLen){
            Panic.panic(Error.BadXIDFileException);
        }


    }

    //  获取xid的状态在文件中的偏移
    private long getXidPosition(long xid){
        return LEN_XID_HEADER_LENGTH + (xid-1)*XID_FIELD_SIZE;
    }

    // 开始事务，返回XID
    public long begin(){
        counterLock.lock();
        try {
            long xid = xidCounter + 1;
            updateXID(xid, FIELD_TRAN_ACTIVE);
            incrXIDCounter();
            return xid;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            counterLock.unlock();
        }
    }

    //更新xid的事务状态为status
    private void updateXID(long xid, byte status){
        long offset = getXidPosition(xid);
        byte[] tmp = new byte[XID_FIELD_SIZE];
        tmp[0] = status;
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try {
            fc.position(offset);
            fc.write(buf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //XID自增，更新XID Header
    private void incrXIDCounter() throws IOException {
        xidCounter++;
        ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    public void commit(long xid){
        updateXID(xid, FIELD_TRAN_COMMITTED);
    }

    public void abort(long xid){
        updateXID(xid, FIELD_TRAN_ABORTED);
    }

    public boolean isActive(long xid){
        if(xid==SUPER_XID) return true;
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    public boolean isCommitted(long xid){
        if(xid==SUPER_XID) return true;
        return checkXID(xid, FIELD_TRAN_COMMITTED);
    }

    public boolean isAborted(long xid){
        if(xid==SUPER_XID) return true;
        return checkXID(xid, FIELD_TRAN_ABORTED);
    }

    public boolean checkXID(long xid, byte status){
        long offset = getXidPosition(xid);
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
        try{
            fc.position(offset);
            fc.read(buf);

        }catch (IOException e){
            Panic.panic(e);
        }
        return buf.array()[0] == status;
    }

    public void close(){
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }
}
