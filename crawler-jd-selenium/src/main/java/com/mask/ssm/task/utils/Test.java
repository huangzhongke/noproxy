package com.mask.ssm.task.utils;

import java.util.concurrent.TimeUnit;

/**
 * @author kee
 * @version 1.0
 * @date 2022/1/20 11:30
 */
public class Test {
    public static void main(String[] args) {
        boolean loop = true;
        while (loop){
            try {
                System.out.println("哈哈");
                TimeUnit.SECONDS.sleep(5);

            }catch (Exception e){
                System.out.println("异常终止");
            }finally {
                System.out.println("程序终止退出");
                loop = false;
            }

        }
    }
}
