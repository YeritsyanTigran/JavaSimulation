package cachemodeler_multyprocessor;

//args[0] - cacheSize
//args[1] - cacheBlockSize
//args[2] - Coherence Protocol [0-Broadcasting,1-Write_Once,2-MSI,3-MESI]
//args[3] - Cache Associative Level [0-1,1-2,3-4,4-8,5-16]
//args[4] - Shared Blocks Quantity [0-16,1-32,2-64,3-128,4-256,5-512,6-1024]
//args[5] - Processors Quantity
//args[6] - Query Probability (Double)
//args[7] - Read Probability (Double)
//args[8] - Shared Probability (Double)
//args[9] - Memory Time
//args[10] - Point Time
//args[11] - Buffer Time
//args[12] - Bus Delay
//args[13] - Sigma
//args[14] - Time
public class SimulatorRunner {
    public static void main(String[] args){
        int RAMSize = 1073741824;
        int cacheSize = 65536;
        int cacheBlockSize = 64;
        CoherenceProtocols cohProt = CoherenceProtocols.WRITE_ONCE;
        int cacheAssoc = 1;
        int procCount = 2;

        args = new String[15];
        for(int i=0;i<15;++i){
            args[i] = (i + 1) + "";
        }
        for(int i=6;i<=8;++i){
            args[i] = 50 + "";
        }

        switch(Integer.parseInt(args[0]))
        {
            case 0:
                cacheSize = 32768;
                break;
            case 1:
                cacheSize = 65536;
                break;
            case 2:
                cacheSize = 131072;
                break;
            case 3:
                cacheSize = 262144;
                break;
            case 4:
                cacheSize = 524288;
                break;
            case 5:
                cacheSize = 1048576;
                break;
            case 7:
                cacheSize = 2097152;
                break;
            case 8:
                cacheSize = 4194304;
                break;
            default:
                cacheSize = 65536;
        }

        switch (Integer.parseInt(args[1]))
        {
            case 0:
                cacheBlockSize = 32;
                break;
            case 1:
                cacheBlockSize = 64;
                break;
            case 2:
                cacheBlockSize = 128;
                break;
            default:
                cacheBlockSize = 64;
        }

        switch (Integer.parseInt(args[2]))
        {
            case 0:
                cohProt = CoherenceProtocols.BROADCAST;
                break;
            case 1:
                cohProt = CoherenceProtocols.WRITE_ONCE;
                break;
            case 2:
                cohProt = CoherenceProtocols.MSI;
                break;
            case 3:
                cohProt = CoherenceProtocols.MESI;
                break;
        }

        switch (Integer.parseInt(args[3]))
        {
            case 0:
                cacheAssoc = 1;
                break;
            case 1:
                cacheAssoc = 2;
                break;
            case 2:
                cacheAssoc = 4;
                break;
            case 3:
                cacheAssoc = 8;
                break;
            case 4:
                cacheAssoc = 16;
                break;
            default:
                cacheAssoc = 4;
        }

        //Collecting parametrs for modeling
        double queryProb = 0.4;
        double readProb = 0.8;
        double sharedProb = 0.1;
        int ShBlocksCount = 32;
        int memTime = 10;
        int pointTime = 1;
        int bufTime = 2;
        int sigma = 64;
        int busDelay = 4;
        int Time = 10000;

        switch (Integer.parseInt(args[4]))
        {
            case 0:
                ShBlocksCount = 16;
                break;
            case 1:
                ShBlocksCount = 32;
                break;
            case 2:
                ShBlocksCount = 64;
                break;
            case 3:
                ShBlocksCount = 128;
                break;
            case 4:
                ShBlocksCount = 256;
                break;
            case 5:
                ShBlocksCount = 512;
                break;
            case 6:
                ShBlocksCount = 1024;
                break;
            default:
                ShBlocksCount = 32;
        }

        boolean allOk = true;

        try
        {
            procCount = Integer.parseInt(args[5]);
            queryProb = Double.parseDouble(args[6]) / 100;
            readProb = Double.parseDouble(args[7]) / 100;
            sharedProb = Double.parseDouble(args[8]) / 100; //SharedSlider
            memTime = Integer.parseInt(args[9]);
            pointTime = Integer.parseInt(args[10]);
            bufTime = Integer.parseInt(args[11]);
            busDelay = Integer.parseInt(args[12]);
            sigma = Integer.parseInt(args[13]);
            Time = Integer.parseInt(args[14]);

            if (memTime<0 || pointTime<0 || bufTime<0 || sigma<0 || Time<0 || busDelay<0)
            {
                allOk = false;
                System.out.println("Invalid param!"
                        + "\nTry again!.");
            }
        }
        catch(Exception ex)
        {
            allOk = false;
            System.out.println("Invalid param!"
                    + "\nTry again!.");
        }
        if (allOk)
        {
            //create structures and start modeling
            SystemParameters sp;
            ModelingParameters mp;
            sp = new SystemParameters(procCount, RAMSize, cacheSize,
                    1, cacheBlockSize, cacheAssoc, cohProt);
            mp = new ModelingParameters(Time, sigma,
                    queryProb, readProb,
                    memTime, pointTime, bufTime, busDelay,
                    sharedProb, ShBlocksCount);
            System.out.println("Began modeling: " + java.util.Calendar.getInstance().getTime() + "\n");
            Multyprocessor mproc = new Multyprocessor(sp, mp);
            mproc.startModeling();
        }
    }
}
