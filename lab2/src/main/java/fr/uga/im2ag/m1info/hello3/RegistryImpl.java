package fr.uga.im2ag.m1info.hello3;

import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;

public class RegistryImpl implements Registry_itf {

    private ConcurrentHashMap<String, Accounting_itf> registeredClients;

    public RegistryImpl() {
        registeredClients = new ConcurrentHashMap<>();
    }

    @Override
    public void register(Accounting_itf client) throws RemoteException {
        String name = client.getName();
        registeredClients.put(name, client);
        System.out.println("Client registered: " + name);
    }

    public boolean isRegistered(String name) {
        return registeredClients.containsKey(name);
    }

    public Accounting_itf getClient(String name) {
        return registeredClients.get(name);
    }
}
