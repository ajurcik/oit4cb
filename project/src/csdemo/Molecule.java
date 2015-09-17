package csdemo;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author xjurc
 */
public class Molecule {
    
    private List<Atom> atoms = new ArrayList<>();
    private List<float[]> snapshots = new ArrayList<>();
    
    public Molecule() {
        // empty molecule
    }
    
    public Molecule(List<List<Atom>> snapshots) {
        for (List<Atom> snapshot : snapshots) {
            addSnapshot(snapshot);
        }
    }
    
    public final void addSnapshot(List<Atom> atoms) {
        if (snapshots.isEmpty()) {
            // add topology
            this.atoms.addAll(atoms);
        }
        addSnapshotPositions(atoms);
    }
    
    private void addSnapshotPositions(List<Atom> atoms) {
        float[] snapshot = new float[3 * atoms.size()];
        for (int i = 0; i < atoms.size(); i++) {
            Atom atom = atoms.get(i);
            snapshot[3 * i] = atom.x;
            snapshot[3 * i + 1] = atom.y;
            snapshot[3 * i + 2] = atom.z;
        }
        snapshots.add(snapshot);
    }
    
    public Atom getAtom(int index) {
        return atoms.get(index);
    }
    
    public List<Atom> getAtoms() {
        return atoms;
    }
    
    public int getAtomCount() {
        return atoms.size();
    }
    
    public float[] getAtomPositions(int snapshot) {
        return snapshots.get(snapshot);
    }
    
    public int getSnapshotCount() {
        return snapshots.size();
    }
    
    public List<float[]> getSnapshots() {
        return snapshots;
    }
    
}
