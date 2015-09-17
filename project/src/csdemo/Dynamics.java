package csdemo;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author xjurc
 */
public class Dynamics {
    
    private Molecule molecule;
    private List<Drug> drugs = new ArrayList<>();

    public Dynamics() {
        // empty dynamics
        molecule = new Molecule();
    }
    
    public Dynamics(Molecule molecule, List<Drug> drugs) {
        this.molecule = molecule;
        this.drugs.addAll(drugs);
    }
    
    public Dynamics(List<List<Atom>> snapshots) {
        molecule = new Molecule(snapshots);
    }
    
    public Molecule getMolecule() {
        return molecule;
    }

    public void addDrug(Drug drug) {
        drugs.add(drug);
    }
    
    public Drug getDrug(int index) {
        return drugs.get(index);
    }
    
    public List<Drug> getDrugs() {
        return drugs;
    }
    
    public int getDrugCount() {
        return drugs.size();
    }
    
    public int getSnapshotCount() {
        return molecule.getSnapshotCount();
    }
    
}
