package csdemo;

import java.util.List;
import javax.vecmath.Point3f;

/**
 *
 * @author xjurc
 */
public class Drug extends Molecule {
    
    public Drug(List<Atom> atoms) {
        // add first snapshot
        addSnapshot(atoms);
    }
    
    public Point3f getCenter(int snapshot, Point3f center) {
        if (center == null) {
            center = new Point3f();
        } else {
            center.set(0f, 0f, 0f);
        }
        
        float[] positions = snapshots.get(snapshot);
        for (int i = 0; i < atoms.size(); i++) {
            center.x += positions[3 * i];
            center.y += positions[3 * i + 1];
            center.z += positions[3 * i + 2];
        }
        center.scale(1f / atoms.size());
        
        return center;
    }
    
}
