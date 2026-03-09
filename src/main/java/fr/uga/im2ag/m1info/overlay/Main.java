package fr.uga.im2ag.m1info.overlay;

import fr.uga.im2ag.m1info.overlay.graph.Graph;
import fr.uga.im2ag.m1info.overlay.graph.GraphLoader;

public class Main{
    public static void main(String []args) {
        Graph g = GraphLoader.load("/home/alioune/M1/s2/ds/src/main/java/fr/uga/im2ag/m1info/overlay/matrix2.txt");
        for (int i = 1; i <= g.size(); i++) {  
            System.out.print(i+":");
            System.out.println(g.neighbors(i));
        }
    }
}

