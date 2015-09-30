/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package csdemo;

/**
 *
 * @author xjurc
 */
public interface DynamicsListener {
    
    public void dynamicsLoaded(Dynamics dynamics);
    
    public void snapshotChanged(int snapshot);
    
}
