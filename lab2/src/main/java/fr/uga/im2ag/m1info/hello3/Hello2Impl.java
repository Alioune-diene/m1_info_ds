
package fr.uga.im2ag.m1info.hello3;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.HashMap;


public class Hello2Impl implements Hello2 {

    private String message;
    private RegistryImpl reg;
    private int numCalls;
    private Map<String, Integer> callsPerClient;

    public Hello2Impl(String s, RegistryImpl registry) {
        message = s;
        numCalls = 0;
        reg = registry;
        callsPerClient = new HashMap<>();
    }

    public String sayHello(Accounting_itf client) throws RemoteException {
        String clientName = client.getName();
        if (! reg.isRegistered(clientName)) {
            return "U should register first !";
        }

        if (! callsPerClient.containsKey(clientName)) {
            callsPerClient.put(clientName, 1);
        } else {
            callsPerClient.put(clientName, callsPerClient.get(clientName) + 1);
        }
        if ((callsPerClient.get(clientName) % 10) == 0) {
            client.numberOfCalls(callsPerClient.get(clientName));
        }
        return message;
    }
}

