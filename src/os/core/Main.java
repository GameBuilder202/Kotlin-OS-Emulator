package os.core;

import os.core.shell.Shell;

public class Main
{
    public static void main(String[] args)
    {
        new Shell(System.in, System.out).boot();
    }
}
