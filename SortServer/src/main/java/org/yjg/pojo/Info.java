package org.yjg.pojo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Created by wangwei on 17-10-21.
 */
@Component
@Scope("prototype")
public class Info {
    private int sumNumber;
    private int recvNumber;

    public int getSumNumber() {
        return sumNumber;
    }

    public void setSumNumber(int sumNumber) {
        this.sumNumber = sumNumber;
    }

    public int getRecvNumber() {
        return recvNumber;
    }

    public void setRecvNumber(int recvNumber) {
        this.recvNumber = recvNumber;
    }
}
