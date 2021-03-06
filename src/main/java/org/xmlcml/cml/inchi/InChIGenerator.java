/**
 *    Copyright 2011 Peter Murray-Rust et. al.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.xmlcml.cml.inchi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jniinchi.INCHI_BOND_TYPE;
import net.sf.jniinchi.INCHI_PARITY;
import net.sf.jniinchi.INCHI_RADICAL;
import net.sf.jniinchi.INCHI_RET;
import net.sf.jniinchi.JniInchiAtom;
import net.sf.jniinchi.JniInchiBond;
import net.sf.jniinchi.JniInchiException;
import net.sf.jniinchi.JniInchiInput;
import net.sf.jniinchi.JniInchiOutput;
import net.sf.jniinchi.JniInchiStereo0D;
import net.sf.jniinchi.JniInchiWrapper;
import nu.xom.Text;

import org.xmlcml.cml.base.CMLElement;
import org.xmlcml.cml.base.CMLElements;
import org.xmlcml.cml.element.CMLAtom;
import org.xmlcml.cml.element.CMLAtomParity;
import org.xmlcml.cml.element.CMLBond;
import org.xmlcml.cml.element.CMLBondStereo;
import org.xmlcml.cml.element.CMLIdentifier;
import org.xmlcml.cml.element.CMLMolecule;
import org.xmlcml.euclid.EuclidConstants;
import org.xmlcml.molutil.ChemicalElement.AS;

/**
 * <p>
 * This class generates the IUPAC International Chemical Identifier (InChI) for
 * a CMLMolecule. It places calls to a JNI wrapper for the InChI C++ library.
 *
 * <p>
 * If the molecule has 3D coordinates for all of its atoms then they will be
 * used, otherwise 2D coordinates will be used if available.
 *
 * Bond stereochemistry and atom parities are supported :-)
 * If 3D coordinates are available then the bond stereochemistry and atom parities
 * should be ignored by InChI.
 * <p>
 *
 * <p>
 *
 * <p>
 * Not typesafe.
 * </p>
 *
 * <h3>Example usage</h3>
 *
 * <code>// Generate factory - if native code does not load</code><br>
 * <code>InChIGeneratorFactory factory = new InChIGeneratorFactory();</code><br>
 * <code>// Get InChIGenerator</code><br>
 * <code>InChIGenerator gen = factory.getInChIGenerator(molecule);</code><br>
 * <code>// Optionally</code><br>
 * <code>gen.setProcessingOptions(new ProcessingOptions[]{ProcessingOptions.USE_BONDS});
 * <code>gen.generate();
 * <code>INCHI_RET ret = gen.getReturnStatus();</code><br>
 * <code>if (ret == INCHI_RET.WARNING) {</code><br>
 * <code>  // InChI generated, but with warning message</code><br>
 * <code>  System.out.println("InChI warning: " + gen.getMessage());</code><br>
 * <code>} else if (ret != INCHI_RET.OKAY) {</code><br>
 * <code>  // InChI generation failed</code><br>
 * <code>  throw new RuntimeException("InChI failed: " + ret.toString()</code><br>
 * <code>    + " [" + gen.getMessage() + S_RSQUARE);</code><br>
 * <code>}</code><br>
 * <code></code><br>
 * <code>String inchi = gen.getInchi();</code><br>
 * <code>String auxinfo = gen.getAuxInfo();</code><br>
 *
 * <p>
 *
 * @author Sam Adams
 * @author Jim Downing
 * @author Daniel Lowe (stereochemistry)
 *
 * @since 5.3
 */
public class InChIGenerator implements EuclidConstants, InChIGeneratorInterface {

    protected JniInchiInput input;

    protected JniInchiOutput output;

    /**
     * Convention to use when constructing CMLIdentifier to hold InChI.
     */
    protected static final String CML_INCHI_CONVENTION = "iupac:inchi";

//    private static final Log LOG = LogFactory.getLog(InChIGenerator.class);

    private static final ProcessingOptions[] DEFAULT_PROCESSING_OPTIONS = new ProcessingOptions[] { ProcessingOptions.USE_BONDS };

    /**
     * Molecule instance refers to.
     */
    protected CMLMolecule molecule;

    private Problems preInChiProblem = null;

    private ProcessingOptions[] processingOptions = DEFAULT_PROCESSING_OPTIONS;

    private boolean generated;

    /**
     * <p>
     * Constructor. Generates InChI from CMLMolecule.
     *
     * <p>
     * Reads atoms, bonds etc from molecule and converts to format InChI library
     * requires, then calls the library.
     *
     * @param molecule
     *            Molecule to generate InChI for.
     * @throws RuntimeException
     */
    protected InChIGenerator(CMLMolecule molecule) {
        this.molecule = molecule;
        try {
            input = new JniInchiInput("");
        } catch (JniInchiException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>
     * Constructor. Generates InChI from CMLMolecule.
     *
     * <p>
     * Reads atoms, bonds etc from molecule and converts to format InChI library
     * requires, then calls the library.
     *
     * @param molecule
     *            Molecule to generate InChI for.
     * @param options
     *            Space delimited string of options to pass to InChI library.
     *            Each option may optionally be preceded by a command line
     *            switch (/ or -).
     * @throws RuntimeException
     */
    protected InChIGenerator(CMLMolecule molecule, String options)
            {
        try {
            this.molecule = molecule;
            input = new JniInchiInput(options);
        } catch (JniInchiException jie) {
            throw new RuntimeException(jie);
        }
    }

    /**
     * <p>
     * Constructor. Generates InChI from CMLMolecule.
     *
     * <p>
     * Reads atoms, bonds etc from molecule and converts to format InChI library
     * requires, then calls the library.
     *
     * @param molecule
     *            Molecule to generate InChI for.
     * @param options
     *            List of INCHI_OPTION.
     * @throws RuntimeException
     */
    protected InChIGenerator(CMLMolecule molecule, List<?> options)
            {
        try {
            this.molecule = molecule;
            input = new JniInchiInput(options);
        } catch (JniInchiException jie) {
            throw new RuntimeException(jie);
        }
    }

    /**
     * Does the work of calling InChI. Can be called only once for each
     * generator.
     *
     * @throws RuntimeException
     * @throws IllegalStateException
     *             if generation has already been done.
     * @since 5.4
     */
    public void generate() {
        if (generated) {
            throw new IllegalStateException("Generator cannot be reused");
        }
        generateInchiFromCMLMolecule(molecule);
        generated = true;
    }

    /**
     * Called from the output getter methods for API back compatibility. This
     * means that errors that formerly throw a checked exception now throw a
     * Runtime.
     */
    private void lazyGenerate() {
        if (!generated) {
            generate();
        }
    }

    /**
     * <p>
     * Reads atoms, bonds etc from molecule and converts to format InChI library
     * requires, then makes call to library, generating InChI.
     *
     * @param molecule
     * @throws RuntimeException
     */
    protected void generateInchiFromCMLMolecule(CMLMolecule molecule)
            {

        List<CMLAtom> atoms = molecule.getAtoms();
        List<CMLBond> bonds = molecule.getBonds();

        // Create map of atom neighbours - required to calculate implicit
        // hydrogen counts
        Map<CMLAtom, List<CMLAtom>> atomNeighbours = new HashMap<CMLAtom, List<CMLAtom>>();
        for (int i = 0; i < atoms.size(); i++) {
            atomNeighbours.put(atoms.get(i), new ArrayList<CMLAtom>(4));
        }
        for (int i = 0; i < bonds.size(); i++) {
            CMLBond bond = (CMLBond) bonds.get(i);
            CMLAtom at0 = bond.getAtom(0);
            CMLAtom at1 = bond.getAtom(1);
            atomNeighbours.get(at0).add(at1);
            atomNeighbours.get(at1).add(at0);
        }

        // Check for 3d coordinates
        boolean all3d = true;
        boolean all2d = true;
        for (int i = 0; i < atoms.size(); i++) {
            CMLAtom atom = atoms.get(i);
            if (!atom.hasCoordinates(CMLElement.CoordinateType.CARTESIAN)) {
                all3d = false;
            }
            if (!atom.hasCoordinates(CMLElement.CoordinateType.TWOD)) {
                all2d = false;
            }
        }

        // Process atoms
        Map<CMLAtom, JniInchiAtom> atomMap = new HashMap<CMLAtom, JniInchiAtom>();
        for (int i = 0; i < atoms.size(); i++) {
            CMLAtom atom = atoms.get(i);
            double x, y, z;
            if (all3d) {
                x = atom.getX3();
                y = atom.getY3();
                z = atom.getZ3();
            } else if (all2d) {
                x = atom.getX2();
                y = atom.getY2();
                z = 0;
            } else {
                x = 0;
                y = 0;
                z = 0;
            }
            String el = atom.getElementType();

            JniInchiAtom iatom = input.addAtom(new JniInchiAtom(x, y, z, el));
            atomMap.put(atom, iatom);

            int charge = atom
                    .getFormalCharge(CMLElement.FormalChargeControl.DEFAULT);
            if (charge != 0) {
                iatom.setCharge(charge);
            }

            try {
                int spinMultiplicity = atom.getSpinMultiplicity();
                if (spinMultiplicity == 0) {
                    iatom.setRadical(INCHI_RADICAL.NONE);
                } else if (spinMultiplicity == 1) {
                    iatom.setRadical(INCHI_RADICAL.SINGLET);
                } else if (spinMultiplicity == 2) {
                    iatom.setRadical(INCHI_RADICAL.DOUBLET);
                } else if (spinMultiplicity == 3) {
                    iatom.setRadical(INCHI_RADICAL.TRIPLET);
                } else {
                    throw new RuntimeException(
                            "Failed to generate InChI: Unsupported spin multiplicity: "
                                    + spinMultiplicity);
                }
            } catch (RuntimeException cre) {
                // Spin multiplicity not set
            }

            try {
                int isotopeNumber = atom.getIsotopeNumber();
                iatom.setIsotopicMass(isotopeNumber);
            } catch (RuntimeException cre) {
                // Isotope number not set
            }

            // Calculate implicit hydrogens
            int hcount;
            if (atom.getHydrogenCountAttribute() == null) {
                hcount = -1;
            } else {
                hcount = atom.getHydrogenCount();

                // getHydrogenCount returns total hydrogens, InChI wants implict
                // so we must remove number of hydrogen ligands
                List<CMLAtom> neighbours = atomNeighbours.get(atom);
                for (int j = 0; j < neighbours.size(); j++) {
                    CMLAtom neigh = neighbours.get(j);
                    if (AS.H.equals(neigh.getElementType())) {
                        hcount--;
                    }
                }

                if (hcount < 0) {
                    throw new RuntimeException(
                            "Negative implicit hydrogen count: " + atom);
                }
            }
            iatom.setImplicitH(hcount);
        }

        for (CMLAtom atom : atoms) {//add atomParities
        	CMLElements<CMLAtomParity> atomParities = atom.getAtomParityElements();//expect none or 1
        	for (CMLAtomParity atomParity : atomParities) {
				CMLAtom[] atomRefs4 = atomParity.getAtomRefs4(molecule);
				if (atomRefs4 != null){
					INCHI_PARITY parity =INCHI_PARITY.UNKNOWN;
					if (atomParity.getIntegerValue() > 0){
						parity =INCHI_PARITY.EVEN;
					}
					else if (atomParity.getIntegerValue() < 0){
						parity =INCHI_PARITY.ODD;
					}
					input.addStereo0D(JniInchiStereo0D.createNewTetrahedralStereo0D(atomMap.get(atom), atomMap.get(atomRefs4[0]), atomMap.get(atomRefs4[1]), atomMap.get(atomRefs4[2]), atomMap.get(atomRefs4[3]), parity));
				}
			}
        }

        if (optionsContains(ProcessingOptions.USE_BONDS)) {
            // Process bonds
            for (int i = 0; i < bonds.size(); i++) {
                CMLBond bond = (CMLBond) bonds.get(i);

                JniInchiAtom at0 = atomMap.get(bond.getAtom(0));
                JniInchiAtom at1 = atomMap.get(bond.getAtom(1));

                INCHI_BOND_TYPE order;
                String bo = bond.getOrder();

                if (CMLBond.isSingle(bo) || bo == null) {
                    order = INCHI_BOND_TYPE.SINGLE;
                } else if (CMLBond.isDouble(bo)) {
                    order = INCHI_BOND_TYPE.DOUBLE;
                } else if (CMLBond.isTriple(bo)) {
                    order = INCHI_BOND_TYPE.TRIPLE;
                } else if (CMLBond.AROMATIC.equals(bo)) {
                    order = INCHI_BOND_TYPE.ALTERN;
                } else {
                    System.out.println("Unsupported bond order: " + bo);
                    preInChiProblem = Problems.BOND_ORDER;
                    return;
                }

                input.addBond(new JniInchiBond(at0, at1, order));
            }
        }
        
        for (CMLBond bond : bonds) {//add bondStereos
        	CMLElements<CMLBondStereo> bondStereos = bond.getBondStereoElements();//expect none or 1
        	for (CMLBondStereo bondStereo : bondStereos) {
				String[] atomRefs4Ids = bondStereo.getAtomRefs4();
				if(atomRefs4Ids==null){
					continue;
				}
				List<JniInchiAtom> jniAtoms = new ArrayList<JniInchiAtom>();
				for (String atomRefId : atomRefs4Ids) {
					jniAtoms.add(atomMap.get(molecule.getAtomById(atomRefId)));
				}
				if (jniAtoms.size()==4){
					if (CMLBond.CIS.equals(bondStereo.getXMLContent())){
						input.addStereo0D(JniInchiStereo0D.createNewDoublebondStereo0D(jniAtoms.get(0), jniAtoms.get(1), jniAtoms.get(2), jniAtoms.get(3), INCHI_PARITY.ODD));
					}
					else if (CMLBond.TRANS.equals(bondStereo.getXMLContent())){
						input.addStereo0D(JniInchiStereo0D.createNewDoublebondStereo0D(jniAtoms.get(0), jniAtoms.get(1), jniAtoms.get(2), jniAtoms.get(3), INCHI_PARITY.EVEN));
					}
				}
			}
        }

        try {
            output = JniInchiWrapper.getInchi(input);
        } catch (JniInchiException jie) {
            throw new RuntimeException("Failed to generate InChI: "
                    + jie.getMessage());
        }
    }

    private boolean optionsContains(ProcessingOptions option) {
        return Arrays.asList(processingOptions).contains(option);
    }

    /**
     * Adds CMLIdentifier containing InChI to CMLMolecule.
     *
     * @throws RuntimeException
     */
    public void appendToMolecule() {
        appendToElement(molecule);
    }

    /**
     * Adds CMLIdentifier containing InChI to specified element.
     *
     * @param element
     * @throws RuntimeException
     */
    public void appendToElement(CMLElement element) {
        if (output.getInchi() == null) {
            throw new RuntimeException("Failed to generate InChI");
        }

        CMLIdentifier identifier = new CMLIdentifier();
        identifier.setConvention(CML_INCHI_CONVENTION);
        identifier.appendChild(new Text(output.getInchi()));

        element.appendChild(identifier);
    }

    /**
     * Gets return status from InChI process. OKAY and WARNING indicate InChI
     * has been generated, in all other cases InChI generation has failed.
     *
     * @return INCHI_RET
     */
    public INCHI_RET getReturnStatus() {
        lazyGenerate();
        return (output.getReturnStatus());
    }
    
    public boolean isOK() {
    	INCHI_RET ret = getReturnStatus();
    	if (INCHI_RET.OKAY.equals(ret) || INCHI_RET.WARNING.equals(ret)){
    		return true;
    	}
    	return false;
	}


    /**
     * Gets generated InChI string.
     *
     * @return string
     */
    public String getInchi() {
        lazyGenerate();
        return (output.getInchi());
    }

    /**
     * Gets generated InChI string.
     *
     * @return string
     */
    public String getAuxInfo() {
        lazyGenerate();
        return (output.getAuxInfo());
    }

    /**
     * Gets generated (error/warning) messages.
     *
     * @return string
     */
    public String getMessage() {
        lazyGenerate();
        return (output.getMessage());
    }

    /**
     * Gets generated log.
     *
     * @return string
     */
    public String getLog() {
        lazyGenerate();
        return (output.getLog());
    }

    /**
     * Get the array (for convenience) of processing options used by this
     * generator.
     *
     * @return array
     * @since 5.4
     */
    public ProcessingOptions[] getProcessingOptions() {
        return processingOptions;
    }

    /**
     * Set the processing options for this generator to use.
     *
     * @param processingOptions
     * @since 5.4
     */
    public void setProcessingOptions(ProcessingOptions[] processingOptions) {
        this.processingOptions = processingOptions;
    }

    /**
     * Has this generator been used (or is it safe to call generate?).
     *
     * @return true if so
     * @since 5.4
     */
    public boolean isGenerated() {
        return generated;
    }

    /**
     * If a problem occurred before we got to InChI it will be here, else this
     * will return null.
     *
     * @return The problem
     * @since 5.4
     */
    public Problems getPreInChiProblem() {
        return preInChiProblem;
    }

}
