package csdemo;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.vecmath.Vector3f;

/**
 * Gromacs file format loader.
 *
 * @author Lukáš Ďurovský <lukasd@mail.muni.cz>
 * @author Filip Volner <filip.volner@gmail.com>
 * @date $Id: GromacsStructureLoader.java 166 2013-04-26 08:25:10Z xsustr $
 */
public class GromacsStructureLoader {
    // currently loaded structure

    private Dynamics structure;
    // currently loaded chain
    //private Chain chain;
    // currently loaded residue
    //private Residue residue;
    // temporary container for atom positions of currently loaded snapshot
    private List<Atom> atomPositions;
    private Map<Integer, List<Atom>> drugPositions;
    // temporary cotainer for residues
    //private ArrayList<Residue> residues;
    //number of last atoms residue sequence
    //private Integer lastResidueSeqNum = null;
    
    private void resetValues(File structureSource) {
        if (structure == null) {
            structure = new Dynamics();
        }

        //structure.setPDBId(FileUtilities.getFileName(structureSource.getName()));
        //structure.setName(structure.getPDBId());

        //chain = new Chain();
        //chain.setIdentifier("A");
        //structure.addChain(chain);
        //residues = new ArrayList<>();
        // temporary container for atom positions in currently loaded snapshot is reset
        atomPositions = new ArrayList<>();
        drugPositions = new LinkedHashMap<>();
    }

    protected Dynamics parseTopologyFile(File sourceFile) throws Exception {
        //final List<List<Atom>> structuresToRet = new ArrayList<>();
        resetValues(sourceFile);
        try {
            BufferedReader inReader = determineInputReader(sourceFile); // new BufferedReader(new FileReader(sourceFile));
            String actualLine = null;

            for (int i = 0; i < 2; i++) {
                if ((actualLine = inReader.readLine()) == null) {
                    throw new Exception("No data in input file");
                }
            }
            int numberOfAtoms = Integer.parseInt(actualLine);
            for (int i = 0; i < numberOfAtoms; i++) {
                actualLine = inReader.readLine();
                if (actualLine == null) {
                    throw new Exception("bad data format in input file");
                }
                this.processGroAtomLine(actualLine);
            }

            //structure.addSource(new File(sourceFile.getAbsolutePath()));
            //structure.addSnapshot(atomPositions);
            
            structure.getMolecule().addSnapshot(atomPositions);
            for (List<Atom> positions : drugPositions.values()) {
                structure.addDrug(new Drug(positions));
            }
            
            //structuresToRet.add(structure);

            return structure;
        } catch (NumberFormatException | IOException ex) {
            throw new Exception("Failed to parse Topology", ex);
        }
    }

    private void processGroAtomLine(String line) {
        String atomIdStr = line.substring(15, 20).trim();
        int atomId = Integer.parseInt(atomIdStr);

        String atomName = line.substring(10, 15).trim();
        String atomElement = String.valueOf(atomName.charAt(0));

        String residueSeqStr = line.substring(0, 5).trim();
        int residueSeq = Integer.parseInt(residueSeqStr);

        String residueIdentifier = line.substring(5, 10).trim();
        if (residueIdentifier.toUpperCase().equals("NA+")
                || residueIdentifier.toUpperCase().equals("CL-")
                || residueIdentifier.equals("SOL")
                || residueIdentifier.equals("NA")
                || residueIdentifier.equals("CL")) {
            // skip ligands and slovent
            return;
        }
        
        boolean drug = false;
        if (residueIdentifier.equals("DRG")) {
            drug = true;
        } else if (residueIdentifier.equals("CYSH") || residueIdentifier.equals("CYS2")) {
            residueIdentifier = "CYS";
        } else if (residueIdentifier.length() == 4
                && (residueIdentifier.startsWith("N") || residueIdentifier.startsWith("C"))) {
            // remove C or N prefix
            residueIdentifier = residueIdentifier.substring(1);
        } else if (residueIdentifier.length() == 4) {
            residueIdentifier = residueIdentifier.substring(0, 3);
        }

        String coordXStr = line.substring(20, 28).trim();
        float coordX = Float.parseFloat(coordXStr) * 10;

        String coordYStr = line.substring(28, 36).trim();
        float coordY = Float.parseFloat(coordYStr) * 10;

        String coordZStr = line.substring(36, 44).trim();
        float coordZ = Float.parseFloat(coordZStr) * 10;

        Atom atom = new Atom();
        //atom.setSerialNumber(atomId);
        //atom.setName(atomName);
        //atom.setElement((byte) Chemistry.getAtomType(atomElement).getAtomicNumber());
        // atom alpha carbon flag is set
        /*if (atom.getElement() == Chemistry.getAtomType("C").getAtomicNumber()) {
            atom.setAlphaCarbon(atom.getName().contains("CA"));
        }*/
        atom.id = atomId;
        atom.r = Utils.getAtomRadius(atomElement);
        if (!drug) {
            atom.v = Utils.getAtomVolume(residueIdentifier, atomName);
        }

        /*if (lastResidueSeqNum == null || lastResidueSeqNum != residueSeq) {
            Residue newResidue = new Residue();
            newResidue.setSequenceNumber(residueSeq);
            newResidue.setId(residueSeq);
            if (residueIdentifier.length() > 3) {
                newResidue.setIdentifier(residueIdentifier.substring(0, 3));
            } else {
                newResidue.setIdentifier(residueIdentifier);
            }
            newResidue.setChain(chain);
            newResidue.addAtom(atom);
            residues.add(newResidue);
            chain.addResidue(newResidue); 
            lastResidueSeqNum = residueSeq;
        } else {
            residues.get(residueSeq - 1).addAtom(atom);
        }*/

        //Vector3f atomCoord = new Vector3f(coordX, coordY, coordZ);
        atom.x = coordX;
        atom.y = coordY;
        atom.z = coordZ;
        
        if (drug) {
            List<Atom> drg = drugPositions.get(residueSeq);
            if (drg == null) {
                drg = new ArrayList<>();
                drugPositions.put(residueSeq, drg);
            }
            drg.add(atom);
        } else {
            atomPositions.add(atom);
        }
    }

    public void parseTrajectoryFile(final File trajectoryFile, final Dynamics structure, final int snapshotLimit) throws Exception {
        parseTrajectoryFile(trajectoryFile, structure, snapshotLimit, null);
    }

    public void parseTrajectoryFile(final File trajectoryFile, final Dynamics structure, final int snapshotLimit, File targetDirectory) throws Exception {
        DataInputStream dis;

        try {
            dis = new DataInputStream(new BufferedInputStream(determineInputStream(trajectoryFile)));


            int counter = structure.getSnapshotCount();
            while (counter < snapshotLimit || snapshotLimit == -1) {
                if (Thread.interrupted()) {
                    return;
                }

                try {
                    if (dis.available() <= 0) {
                        break;
                    }

                    int magic;
                    int atomsCount;
                    int simulationStep;
                    float simulationTime;
                    magic = dis.readInt();
                    atomsCount = dis.readInt();
                    simulationStep = dis.readInt();
                    simulationTime = dis.readFloat();

                    float[][] box = new float[3][3];
                    for (int i = 0; i < 3; i++) {
                        for (int j = 0; j < 3; j++) {
                            box[i][j] = dis.readFloat();
                        }
                    }

                    xtc3dfcoords(trajectoryFile, dis, structure, counter, targetDirectory);
                } catch (IOException ex) {
                    Logger.getLogger(GromacsStructureLoader.class.getName()).log(Level.SEVERE, null, ex);
                    break;
                }

                counter++;
                
                if (counter > 0 && counter % 100 == 0) {
                    System.out.println("Loaded " + counter + " snapshots");
                }
            }

            dis.close();
        } catch (FileNotFoundException ex) {
            throw new Exception("Failed to parse Trajectory", ex);
            //Logger.getLogger(GromacsStructureLoader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            throw new Exception("Failed to parse Trajectory", ex);
            //Logger.getLogger(GromacsStructureLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public int getNumberOfSnapshots(File trajectory) throws Exception {
        int numberOfSnapshots = 0;

        //for (File trajFile : sourceFiles) {
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(determineInputStream(trajectory)))) {
                while (true) {
                    try {
                        if (dis.available() <= 0) {
                            break;
                        }

                        int magic;
                        int atomsCount;
                        int simulationStep;
                        float simulationTime;
                        magic = dis.readInt();
                        atomsCount = dis.readInt();
                        simulationStep = dis.readInt();
                        simulationTime = dis.readFloat();

                        float[][] box = new float[3][3];
                        for (int i = 0; i < 3; i++) {
                            for (int j = 0; j < 3; j++) {
                                box[i][j] = dis.readFloat();
                            }
                        }

                        xtc3dfcoords(trajectory, dis, null, numberOfSnapshots, null);
                    } catch (IOException ex) {
                        Logger.getLogger(GromacsStructureLoader.class.getName()).log(Level.SEVERE, null, ex);
                        break;
                    }

                    numberOfSnapshots++;
                }

                dis.close();
            } catch (FileNotFoundException ex) {
                throw new Exception("Failed to parse Trajectory", ex);
                //Logger.getLogger(GromacsStructureLoader.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                throw new Exception("Failed to parse Trajectory", ex);
                //Logger.getLogger(GromacsStructureLoader.class.getName()).log(Level.SEVERE, null, ex);
            }
        //}
        return numberOfSnapshots;
    }

    public Dynamics loadDynamics(File topology, File trajectory) throws Exception {
        return loadDynamics(topology, trajectory, -1);
    }
    
    public Dynamics loadDynamics(File topology, File trajectory, int limit) throws Exception {
        return loadStructureSync(topology, new File[] { trajectory }, limit);
    }
    
    public Dynamics loadStructureSync(final File topologyFile, final File[] trajectoryFiles, final int snapshotLimit) throws Exception {
        final Dynamics structures;

        try {
            // new structure is created
            structure = new Dynamics();
            //structure.setTopologySource(topologyFile);
            //structure.setSource(Arrays.asList(trajectoryFiles));

            Dynamics conStructures = parseTopologyFile(topologyFile);

            if (trajectoryFiles != null) {
                for (File accFile : trajectoryFiles) {
                    parseTrajectoryFile(accFile, conStructures, snapshotLimit);
                }
            }

            structures = conStructures;
        } catch (Exception sle) {
            //log.error(sle.getMessage());
            /*WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(null, "Loading of structure file " + topologyFile + " failed.\nCould not open specified file or the file is not a valid Gromacs file!");
                    //ErrorDialog dialog = new ErrorDialog(WindowManager.getDefault().getMainWindow(), "Loading of structure file " + sourceFile.getName() + " failed.\nCould not open specified file or the file is not a valid PDB file!");
                    //dialog.setVisible(true);
                }
            });*/
            throw sle;
        }

        // loaded structures from the file are added to the controller
        //structureController.addStructures(structures, null);
        //structures.clear();
        return structures;
    }

    private final int xtc_magicints[] = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 10, 12, 16, 20, 25, 32, 40, 50, 64,
        80, 101, 128, 161, 203, 256, 322, 406, 512, 645, 812, 1024, 1290,
        1625, 2048, 2580, 3250, 4096, 5060, 6501, 8192, 10321, 13003, 16384,
        20642, 26007, 32768, 41285, 52015, 65536, 82570, 104031, 131072,
        165140, 208063, 262144, 330280, 416127, 524287, 660561, 832255,
        1048576, 1321122, 1664510, 2097152, 2642245, 3329021, 4194304,
        5284491, 6658042, 8388607, 10568983, 13316085, 16777216};
    private final int FIRSTIDX = 9;

    private void xtc3dfcoords(File trajectoryFile, DataInputStream dr, Dynamics structure, int snapshotId, File targetDirectory) throws Exception {
        int size = 0;
        float precision;

        List<Integer> buf = new ArrayList<>(3);

        int minint[] = new int[3];
        int maxint[] = new int[3];
        int smallidx;

        int sizeint[] = new int[3];
        int sizesmall[] = new int[3];
        int bitsizeint[] = new int[3];
        int size3;
        int flag, k;
        int small, smaller, i, is_smaller, run;
        int tmp;
        int thiscoord[] = new int[3];
        int prevcoord[] = new int[3];

        int lsize;
        int bitsize;
        float inv_precision;

        List<Float> result = new ArrayList<>();

        try {
            lsize = dr.readInt();

            if (size != 0 && lsize != size) {
                System.err.println("size lsize error");
                return;
            }

            size = lsize;
            size3 = size * 3;

            if (size <= 9) {
                for (int a = 0; a < size; a++) {
                    result.add(dr.readFloat());
                }

                ArrayList<Vector3f> atompostitions = new ArrayList<>();
                for (int atomcount = 0; atomcount < size3; atomcount += 3) {
                    atompostitions.add(new Vector3f(result.get(atomcount) * 10, result.get(atomcount + 1) * 10, result.get(atomcount + 2) * 10));
                }

                if (structure != null) {
                    /*if (structure.getAtomCount() != atompostitions.size()) {
                        throw new Exception("Count of atoms in topology and trajectory differ!");
                    }*/

                    //structure.addSource(new File(trajectoryFile.getAbsolutePath() + "[" + snapshotId + "]"));
                    //structure.addSnapshot(atompostitions);
                    throw new UnsupportedOperationException("Adding less than 3 atoms not supported");

                    /*if (targetDirectory != null) {
                        exportToPdbSequence(structure, targetDirectory);
                        structure.removeSnapshot(structure.getSnapshotCount() - 1);
                    }*/
                }

                return;
            }
            precision = dr.readFloat();

            buf.add(0);
            buf.add(0);
            buf.add(0);

            minint[0] = dr.readInt();
            minint[1] = dr.readInt();
            minint[2] = dr.readInt();
            maxint[0] = dr.readInt();
            maxint[1] = dr.readInt();
            maxint[2] = dr.readInt();

            sizeint[0] = maxint[0] - minint[0] + 1;
            sizeint[1] = maxint[1] - minint[1] + 1;
            sizeint[2] = maxint[2] - minint[2] + 1;

            /* check if one of the sizes is to big to be multiplied */
            if ((sizeint[0] | sizeint[1] | sizeint[2]) > 0xffffff) {
                bitsizeint[0] = xtc_sizeofint(sizeint[0]);
                bitsizeint[1] = xtc_sizeofint(sizeint[1]);
                bitsizeint[2] = xtc_sizeofint(sizeint[2]);
                bitsize = 0; /* flag the use of large sizes */

            } else {
                bitsize = xtc_sizeofints(3, sizeint);
            }

            smallidx = dr.readInt();

            smaller = xtc_magicints[FIRSTIDX > smallidx - 1 ? FIRSTIDX : smallidx - 1] / 2;
            small = xtc_magicints[smallidx] / 2;
            sizesmall[0] = sizesmall[1] = sizesmall[2] = xtc_magicints[smallidx];

            buf.set(0, dr.readInt());

            compressedCoordsData = new int[buf.get(0)];

            for (int b = 0; b < compressedCoordsData.length; b++) {
                compressedCoordsData[b] = dr.readByte() & 0xff;
            }

            // docist modulo
            if (buf.get(0) % 4 > 0) {
                dr.skip(4 - (buf.get(0) % 4));
            }

            buf.set(0, 0);
            buf.set(1, 0);
            buf.set(2, 0);

            inv_precision = 1.0f / precision;
            run = 0;
            i = 0;

            while (i < lsize) {
                if (bitsize == 0) {
                    thiscoord[0] = xtc_receivebits(buf, bitsizeint[0]);
                    thiscoord[1] = xtc_receivebits(buf, bitsizeint[1]);
                    thiscoord[2] = xtc_receivebits(buf, bitsizeint[2]);
                } else {
                    xtc_receiveints(buf, 3, bitsize, sizeint, thiscoord);
                }

                i++;

                thiscoord[0] += minint[0];
                thiscoord[1] += minint[1];
                thiscoord[2] += minint[2];

                prevcoord[0] = thiscoord[0];
                prevcoord[1] = thiscoord[1];
                prevcoord[2] = thiscoord[2];

                flag = xtc_receivebits(buf, 1);
                is_smaller = 0;
                if (flag == 1) {
                    run = xtc_receivebits(buf, 5);
                    is_smaller = run % 3;
                    run -= is_smaller;
                    is_smaller--;
                }
                if (run > 0) {
                    for (k = 0; k < run; k += 3) {
                        xtc_receiveints(buf, 3, smallidx, sizesmall, thiscoord);
                        i++;

                        thiscoord[0] += prevcoord[0] - small;
                        thiscoord[1] += prevcoord[1] - small;
                        thiscoord[2] += prevcoord[2] - small;
                        if (k == 0) {
                            /* interchange first with second atom for better
                             * compression of water molecules
                             */
                            tmp = thiscoord[0];
                            thiscoord[0] = prevcoord[0];
                            prevcoord[0] = tmp;
                            tmp = thiscoord[1];
                            thiscoord[1] = prevcoord[1];
                            prevcoord[1] = tmp;
                            tmp = thiscoord[2];
                            thiscoord[2] = prevcoord[2];
                            prevcoord[2] = tmp;

                            result.add(prevcoord[0] * inv_precision);
                            result.add(prevcoord[1] * inv_precision);
                            result.add(prevcoord[2] * inv_precision);
                        } else {
                            prevcoord[0] = thiscoord[0];
                            prevcoord[1] = thiscoord[1];
                            prevcoord[2] = thiscoord[2];
                        }
                        result.add(thiscoord[0] * inv_precision);
                        result.add(thiscoord[1] * inv_precision);
                        result.add(thiscoord[2] * inv_precision);
                    }
                } else {
                    result.add(thiscoord[0] * inv_precision);
                    result.add(thiscoord[1] * inv_precision);
                    result.add(thiscoord[2] * inv_precision);
                }
                smallidx += is_smaller;
                if (is_smaller < 0) {
                    small = smaller;
                    if (smallidx > FIRSTIDX) {
                        smaller = xtc_magicints[smallidx - 1] / 2;
                    } else {
                        smaller = 0;
                    }
                } else if (is_smaller > 0) {
                    smaller = small;
                    small = xtc_magicints[smallidx] / 2;
                }
                sizesmall[0] = sizesmall[1] = sizesmall[2] = xtc_magicints[smallidx];
            }

//            for (Float r : result) {
//                System.out.print(r + "|");
//            }
//            System.out.println();
            List<Vector3f> atompostitions = new ArrayList<>();
            for (int atomcount = 0; atomcount < size3; atomcount += 3) {
                atompostitions.add(new Vector3f(result.get(atomcount) * 10, result.get(atomcount + 1) * 10, result.get(atomcount + 2) * 10));
            }

            if (structure != null) {
                /*if (structure.getAtomCount() != atompostitions.size()) {
                    //throw new Exception("Count of atoms in topology and trajectory differ!");
                    //System.err.println("Warning: count of atoms in topology and trajectory differ!");
                    atompostitions = atompostitions.subList(0, structure.getAtomCount());
                }*/

                //structure.addSource(new File(trajectoryFile.getAbsolutePath() + "[" + snapshotId + "]"));
                //structure.addSnapshot(atompostitions);
                List<Atom> snapshot = new ArrayList<>();
                
                Molecule molecule = structure.getMolecule();
                for (Atom atom : molecule.getAtoms()) {
                    Atom pos = new Atom(atom);
                    atom.setPosition(atompostitions.get(atom.id - 1));
                    snapshot.add(pos);
                }
                molecule.addSnapshot(snapshot);
                
                for (Drug drug : structure.getDrugs()) {
                    snapshot.clear();
                    for (Atom atom : drug.getAtoms()) {
                        Atom pos = new Atom(atom);
                        atom.setPosition(atompostitions.get(atom.id - 1));
                        snapshot.add(pos);
                    }
                    drug.addSnapshot(snapshot);
                }

                /*if (targetDirectory != null) {
                    exportToPdbSequence(structure, targetDirectory);
                    structure.removeSnapshot(structure.getSnapshotCount() - 1);
                }*/
            }

        } catch (IOException ex) {
            Logger.getLogger(GromacsStructureLoader.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void xtc_receiveints(List<Integer> buf, int nints, int nbits, int[] sizes, int[] nums) {
        try {
            int[] bytes = new int[32];
            int i, j, nbytes, p, num;

            bytes[1] = bytes[2] = bytes[3] = 0;
            nbytes = 0;
            while (nbits > 8) {
                bytes[nbytes++] = xtc_receivebits(buf, 8);
                nbits -= 8;
            }
            if (nbits > 0) {
                bytes[nbytes++] = xtc_receivebits(buf, nbits);
            }
            for (i = nints - 1; i > 0; i--) {
                num = 0;
                for (j = nbytes - 1; j >= 0; j--) {
                    num = (num << 8) | bytes[j];
                    p = num / sizes[i];
                    bytes[j] = p;
                    num = num - p * sizes[i];
                }
                nums[i] = num;
            }
            nums[0] = bytes[0] | (bytes[1] << 8) | (bytes[2] << 16) | (bytes[3] << 24);

        } catch (Exception e) {
            throw e;
        }
    }

    private int xtc_receivebits(/*int[] buf*/List<Integer> buf, int nbits) {
        int cnt, num;
        int lastbits, lastbyte;

        //unsigned char * cbuf;
        int mask = (1 << nbits) - 1;

        //tady se proste posune v tom bufferu o 3 a pokracuje se
        //s hotovymi nactenymi hodnotami. To my nepotrebujeme, mame nactena data v compressedCoordsData
        //a z puvodniho buf pole pouzivame jen prvni tri ridici hodnoty
        int[] cbuf = compressedCoordsData;

        cnt = buf.get(0);
        lastbits = buf.get(1);
        lastbyte = buf.get(2);

        num = 0;
        while (nbits >= 8) {
            lastbyte = (lastbyte << 8) | cbuf[cnt++];
            num |= (lastbyte >> lastbits) << (nbits - 8);
            nbits -= 8;
        }
        if (nbits > 0) {
            if (lastbits < nbits) {
                lastbits += 8;
                lastbyte = (lastbyte << 8) | cbuf[cnt++];
            }
            lastbits -= nbits;
            num |= (lastbyte >> lastbits) & ((1 << nbits) - 1);
        }
        num &= mask;

        buf.set(0, cnt);
        buf.set(1, lastbits);
        buf.set(2, lastbyte);
        return num;
    }
    private int[] compressedCoordsData;

    private int xtc_sizeofints(int intsCount, int[] ints) {
        long size;
        int bytesCount = 1;
        int bitsCount = 0;
        long bytes[] = new long[32];
        int byteCount;
        long tmp;

        bytes[0] = 1;

        for (int i = 0; i < intsCount; i++) {
            tmp = 0;
            for (byteCount = 0; byteCount < bytesCount; byteCount++) {
                tmp = bytes[byteCount] * ints[i] + tmp;
                bytes[byteCount] = tmp & 0xff;
                tmp >>= 8;
            }
            while (tmp != 0) {
                bytes[byteCount] = tmp & 0xff;
                byteCount++;
                tmp >>= 8;
            }
            bytesCount = byteCount;
        }
        size = 1;
        bytesCount--;

        while (bytes[bytesCount] >= size) {
            bitsCount++;
            size *= 2;
        }

        return bitsCount + bytesCount * 8;
    }

    private int xtc_sizeofint(int size) {
        long num = 1;
        int nbits = 0;

        while (size >= num && nbits < 32) {
            nbits++;
            num <<= 1;
        }

        return nbits;
    }
    
    public static BufferedReader determineInputReader(File file) throws IOException {
        BufferedReader in;

        if (file.getAbsolutePath().toLowerCase().endsWith(".gz")) {
            GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(file));
            in = new BufferedReader(new InputStreamReader(gzip));
        } else if (file.getAbsolutePath().toLowerCase().endsWith(".zip")) {
            ZipFile zipFile = new ZipFile(file);
            if (zipFile.entries().hasMoreElements()) {
                ZipEntry entry = zipFile.entries().nextElement();
                in = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
            } else {
                throw new IllegalArgumentException("Could not determine reader for " + file.getName());
            }
        } else {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        }

        return in;
    }
    
    public static InputStream determineInputStream(File file) throws IOException {
        if (file.getAbsolutePath().toLowerCase().endsWith(".gz")) { //if it is GZ, we need GZipInputStream
            return new GZIPInputStream(new FileInputStream(file));
        } else if (file.getAbsolutePath().toLowerCase().endsWith(".zip")) { //if it is ZIP, we need ZipInputStream
            ZipFile zipFile = new ZipFile(file);
            if (zipFile.entries().hasMoreElements()) {
                if (zipFile.entries().hasMoreElements()) {
                    ZipEntry entry = zipFile.entries().nextElement();
                    return zipFile.getInputStream(entry);
                }
            }

        } else {
            return new FileInputStream(file);
        }
        throw new IllegalArgumentException("Could not determine stream for " + file.getName());
    }
}
