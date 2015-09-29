package csdemo;

import java.util.ArrayList;
import java.util.List;
import javax.vecmath.Vector3f;

/**
 *
 * @author xjurc
 */
public class Molecule {
    
    private static final float TOLERANCE_MIN = 0.4f;
    private static final float TOLERANCE_MAX = 0.56f;
    
    protected List<Atom> atoms = new ArrayList<>();
    protected List<float[]> snapshots = new ArrayList<>();
    protected List<Bond> bonds = new ArrayList<>();
    
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
    
    public void computeBonds() {
        List<Integer> residues = new ArrayList<>();
        int currentResidueId = -1;
        for (int i = 0; i < atoms.size(); i++) {
            Atom atom = atoms.get(i);
            if (atom.residueId != currentResidueId) {
                residues.add(i);
                currentResidueId = atom.residueId;
            }
        }
        // add last fake residue
        residues.add(atoms.size());
        
        for (int i = 0; i < residues.size() - 1; i++) {
            Integer residueStart = residues.get(i);
            Integer residueEnd = residues.get(i + 1);
            computeBondsInResidue(residueStart, residueEnd);
        }
        
        computeBondsBetweenResidues(residues);
    }
    
    private void computeBondsInResidue(int start, int end) {
        Atom firstAtom, secondAtom;
        Vector3f posFirst, posSecond;

        for (int i = start; i < end - 1; i++) {
            // gets first atom for bond
            firstAtom = atoms.get(i);
            for (int j = i + 1; j < end; j++) {
                // gets second atom for bond
                secondAtom = atoms.get(j);
                // if bond between the atoms doesn't already exists, it is added according to the computation result
                if (true /*i < j*/) {
                    // gets position of atoms
                    posFirst = firstAtom;
                    posSecond = secondAtom;
                    // computes the length of bond between first and second atom
                    float x = posFirst.x - posSecond.x;
                    float y = posFirst.y - posSecond.y;
                    float z = posFirst.z - posSecond.z;
                    float mag = x * x + y * y + z * z;
                    float tolerance = Utils.getCovalentRadius(firstAtom.type) + Utils.getCovalentRadius(secondAtom.type) + TOLERANCE_MAX;
                    if (mag > (TOLERANCE_MIN * TOLERANCE_MIN) && mag < (tolerance * tolerance)) {
                        bonds.add(new Bond(i, j));
                    }
                }
            }
        }
    }
    
    private void computeBondsBetweenResidues(List<Integer> residues) {
        int resFirst, resSecond, resSecondEnd;
        int first, second;
        Bond bond;

        if (residues.size() > 1) {
            for (int i = 0; i < residues.size() - 2; i++) {
                resFirst = residues.get(i);
                resSecond = residues.get(i + 1);
                resSecondEnd = residues.get(i + 2);
                // finds the "C" atom in the first residue
                first = findAtom(resFirst, resSecond, "C");
                // finds the "N" atom in the second residue
                second = findAtom(resSecond, resSecondEnd, "N");
                // if both atoms were found and bond doesn't already exist, it connects them with bond
                if (first >= 0 && second >= 0) {
                    if (/*!firstAtom.hasBondTo(secondAtom)*/ true) {
                        Atom firstAtom = atoms.get(first);
                        Atom secondAtom = atoms.get(second);
                        float x = firstAtom.x - secondAtom.x;
                        float y = firstAtom.y - secondAtom.y;
                        float z = firstAtom.z - secondAtom.z;
                        float mag = x * x + y * y + z * z;
                        float tolerance = Utils.getCovalentRadius(firstAtom.type) + Utils.getCovalentRadius(secondAtom.type) + TOLERANCE_MAX;
                        if (mag > (TOLERANCE_MIN * TOLERANCE_MIN) && mag < (tolerance * tolerance)) {
                            bonds.add(new Bond(first, second));
                        }
                    }
                }
            }
        }
    }
    
    private int findAtom(int start, int end, String name) {
        for (int i = start; i < end; i++) {
            Atom atom = atoms.get(i);
            if (atom.name.equals(name)) {
                return i;
            }
        }
        return -1;
    }
    
    public List<Bond> getBonds() {
        return bonds;
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
