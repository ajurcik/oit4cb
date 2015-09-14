package csdemo;

import javax.vecmath.Tuple3f;
import javax.vecmath.Vector3f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class Atom extends Vector3f {
    
    public float r;
    public float v;
    
    public void setPosition(Tuple3f position) {
        set(position);
    }
    
}
