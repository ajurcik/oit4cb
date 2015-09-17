package csdemo;

import javax.vecmath.Tuple3f;
import javax.vecmath.Vector3f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class Atom extends Vector3f {
    
    public int id;
    public float r;
    public float v;
    
    public Atom() {
        // empty atom
    }
    
    public Atom(Atom atom) {
        id = atom.id;
        r = atom.r;
        v = atom.v;
        setPosition(atom);
    }
    
    public final void setPosition(Tuple3f position) {
        set(position);
    }
    
}
