package csdemo;

import java.util.List;

/**
 *
 * @author xjurc
 */
public class Drug extends Molecule {
    
    public Drug(List<Atom> atoms) {
        // add first snapshot
        addSnapshot(atoms);
    }
    
}
