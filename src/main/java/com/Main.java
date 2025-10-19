package com;

import com.helpers.Helpers;

public class Main {
    public static void main(String[] args) {
        System.out.println(Helpers.isValidId(10));
        System.out.println(Helpers.isValidIP("192.168.0.1"));
        System.out.println(Helpers.isValidIP("192.168.0.2000"));
        System.out.println(Helpers.isValidPort(8080));
    }
}