/*
Copyright 2014 Weswit s.r.l.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.lightstreamer.adapters.Dart.engine3D;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.lightstreamer.adapters.Dart.Constants;
import com.lightstreamer.adapters.Dart.room.User;
import com.lightstreamer.interfaces.metadata.CreditsException;

public class World extends Thread {
    
    private static final int ENTER = 1;
    private static final int EXIT = 2;
    
    
    private static final boolean REALTIME = true;
    private static final boolean SNAPSHOT = false;
    
    
    private Executor executor =  Executors.newSingleThreadExecutor();

    private String id;
    private UniverseListener listener;
    
    private ConcurrentHashMap<String,Dart> darts = new ConcurrentHashMap<String,Dart>();
    private Object handle = null;
    private Object scoreHandle = null;
    

    private int frameInterval = 0;
    private double factorWorld = 1.0;
    
    private boolean stop = false;
    
    
    World(String id, UniverseListener listener, int frameInterval)  {
        this.id = id;
        this.listener = listener;
        
        this.frameInterval = frameInterval;
        this.factorWorld = (double)(this.frameInterval / Constants.BASE_RATE);
    }
    
    synchronized void setFrameInterval(int frameInterval) {
        this.frameInterval = frameInterval;
        this.factorWorld = (double)(this.frameInterval / Constants.BASE_RATE);
    }

    synchronized boolean isListened() {
        return this.handle != null || this.scoreHandle != null;
    }
    
    synchronized boolean isEmpty() {
        return darts.isEmpty();
    }
    
    synchronized void setHandle(Object handle) {
        this.handle = handle;
        
        if (this.handle != null) {
            Enumeration<Dart> players = this.darts.elements();
            while(players.hasMoreElements()) {
                Dart player = players.nextElement();
                this.sendPlayerStatus(player.getId(), this.id, this.handle, player, ENTER, SNAPSHOT);
            }
            
            final String fid = this.id;
            final Object fhandle = this.handle;
            executor.execute(new Runnable() {
                public void run() {
                    listener.onWorldComplete(fid,fhandle);
                }
            });
        }
    }
    
    synchronized void setScoreHandle(Object handle) {
        this.scoreHandle = handle;
        //TODO snapshot?
    }
    
    synchronized void addUser(User user) {
        String id = user.getId();
        if (this.darts.containsKey(id)) {
            return;
        }
        Dart player = new Dart(user,id,this);
        
        this.darts.put(id,player); 
        this.sendPlayerStatus(id, this.id, this.handle, player, ENTER, REALTIME);
    }
    

    synchronized void removeUser(String id) {
        this.darts.remove(id);
        this.sendPlayerStatus(id, this.id, this.handle, null, EXIT, REALTIME);
    }
    
    synchronized void armageddon() {
        this.stop = true;
    }
    
    @Override
    public void run () {
        
        while (!stop) {
                        
            Enumeration<Dart> players = this.darts.elements();
            while(players.hasMoreElements()) {
                Dart player = players.nextElement();
                              
                player.translate(this.factorWorld);
                
                this.sendPlayerPosition(player.getId(), this.id, this.handle, player);
            }
            
            try {
                Thread.sleep(this.frameInterval);
            } catch (InterruptedException ie) {
            }
        }
    }
      
    private synchronized void sendPlayerStatus(final String id, final String worldId, final Object worldHandle, Dart player, final int updateType, final boolean isRealTime) {
        if (updateType == ENTER) {
            final HashMap<String,String> currentPosition = new HashMap<String,String>();
            player.fillPositionMap(currentPosition);
            
            final HashMap<String,String> currentImpulses = new HashMap<String,String>();
            player.fillImpulseMap(currentImpulses);
            
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    listener.onPlayerCreated(id,worldId,worldHandle,currentPosition,currentImpulses,isRealTime);
                } 
            });
        } else if (updateType == EXIT) {
           executor.execute(new Runnable() {
               @Override
               public void run() {
                   listener.onPlayerDisposed(id,worldId,worldHandle);
               } 
           });
        }
    }
    
    private synchronized void sendPlayerPosition(final String id, final String worldId, final Object worldHandle, Dart player) {
        final HashMap<String, String> currentPosition = new HashMap<String, String>();
        player.fillPositionMap(currentPosition);
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                listener.onPlayerMoved(id, worldId, worldHandle, currentPosition);
            } 
        });
    }
    
    
    
    public synchronized void sendPlayerScore(final String playerId, final int score) {
        if (!this.darts.containsKey(playerId)) {
            return;
        }
        
        final String worldId = this.id;
        final Object worldHandle = this.scoreHandle;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                listener.onPlayerScore(playerId, score, worldId, worldHandle);
            } 
        });
    }

    public synchronized void block(String playerId) throws CreditsException {
        if (!this.darts.containsKey(playerId)) {
            return;
        }
        Dart player = this.darts.get(playerId);
        player.block();
    }
    
    public synchronized void release(String playerId, double x, double y, double z) throws CreditsException {
        if (!this.darts.containsKey(playerId)) {
            return;
        }
        Dart player = this.darts.get(playerId);
        player.throwDart(x,y,z);
    }

    public synchronized void move(String playerId, double x, double y, double z) throws CreditsException {
        if (!this.darts.containsKey(playerId)) {
            return;
        }
        Dart player = this.darts.get(playerId);
        player.forcePosition(x, y, z);
    }
    
    
    

}
