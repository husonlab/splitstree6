/*
 * mtMAMModel.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 */
package splitstree6.models.proteinModels;

/**
 * mtNMAM model
 * David Bryant, 2005?
 */
public class mtMAMModel extends ProteinModel {

	public mtMAMModel() {

		/*
         * Protein evolution matrix based on
	
		rate matrix and aa frequencies, estimated from
		the 12 mt proteins (atp6 atp8 cox1 cox2 cox3 cytb nd1 nd2 nd3 nd4 nd4l
		nd5) on the same strand of the mitochondrial DNA (3331 sites).  The
		data are from 20 species of mammals and three close outgroups
		(wallaroo, opossum, and platypus).  The model used is
		REVaa+dGamma(K=8) with the estimated gamma parameter to be 0.37.  The
		first part is S_ij = S_ji, and the second part has the amino acid
		frequencies (PI_i).  The substitution rate from amino acid i to j is
		Q_ij=S_ij*PI_j.
		
		The data are from
		
		   Cao, Y. et al. 1998 Conflict amongst individual mitochondrial proteins
		   in resolving the phylogeny of eutherian orders.  Journal of
		   Molecular Evolution 15:1600-1611.
		
		
		The species are listed below
		
		1 SB17F Homo sapiens (African) # D38112
		2 CHIMP Pan troglodytes (chimpanzee) # D38113
		3 PyGC Pan paniscus (bonobo) # D38116
		4 GORIL Gorilla gorilla (gorilla) # D38114
		5 ORANG Pongo pygmaeus (orangutan) # D38115
		6 Ponpy Pongo pygmaeus abelii (Sumatran orangutan) # X97707
		7 Hylla Hylobates lar (common gibbon) # X99256 (lar gibbon)
		8 Phovi Phoca vitulina (harbor seal) # X63726
		9 Halgr Halichoerus grypus (grey seal) # X72004
		10 Felca Felis catus (cat) # U20753
		11 Equca Equus caballus (horse) # X79547
		12 Rhiun Rhinoceros unicornis (Indian rhinoceros) # X97336
		13 Bosta Bos taurus (cow) # J01394
		14 Balph Balaenoptera physalus (fin whale) # X61145
		15 Balmu Balaenoptera musculus (blue whale) # X72204
		16 Ratno Rattus norvegicus (rat) # X14848
		17 Musmu Mus musculus (mouse) # J01420
		18 Macro Macropus robustus (wallaroo) # Y10524
		19 Didvi Didelphis virginiana (opossum) # Z29573
		20 Ornan Ornithorhynchus anatinus (platypus) # X83427
		
		The results are details of the model are published in
		
		   Yang, Z., R. Nielsen, and M. Hasegawa.  1998.  Models of amino acid
		   substitution and applications to Mitochondrial protein evolution, 
		   Molecular Biology and Evolution 15:1600-1611.

		The model was taken from the .dat files distributed with PAML3.1d
	*/



		/* For efficiency, we precompute the eigenvalue decomposition
		 *     V'DV = Pi^(1/2) Q Pi(-1/2)
		 * We used matlab for this.
		 */

		this.evals = new double[]{
				-3.47106,
				-2.43299,
				-1.86868,
				-1.41516,
				-1.34567,
				-1.12012,
				-0.909988,
				-0.806833,
				-0.677302,
				-0.58377,
				-0.465092,
				-0.401934,
				-0.378692,
				-0.329542,
				-0.316496,
				-0.172153,
				-0.150087,
				-0.137198,
				-0.0530401,
				1.08718e-16};

		this.evecs = new double[][]{{-0.061892, -0.104464, 0.252994, -0.551467, -0.302325, -0.232315, 0.298629, -0.319706, 0.230171, -0.301058, -0.153457, 0.0478327, 0.0933173, 0.0651439, -0.113751, 0.022507, -0.0228138, -0.112969, -0.0451829, 0.263059},
				{0.000163193, 0.000238793, -0.0017701, -0.00400027, 0.0209905, 0.00825477, -0.00832569, -0.0183669, 0.00964842, 0.0381741, 0.0536265, -0.0698326, 0.0820658, 0.0910946, -0.245337, 0.659204, 0.441764, 0.516111, -0.00795845, 0.135647},
				{0.00153098, 0.00410714, -0.0356553, -0.264086, -0.0588648, 0.267253, -0.728526, -0.0119742, 0.198921, -0.107295, -0.132646, -0.321329, 0.228147, -0.0262448, 0.203289, -0.0883845, -0.00709689, 0.0891066, -0.0372539, 0.2},
				{-0.00063062, 0.000173264, 0.00335865, 0.0554709, 0.0145961, -0.0836946, 0.348209, 0.0118874, -0.234688, 0.302337, -0.41983, -0.611939, 0.197769, 0.0133238, 0.0172545, -0.234859, -0.0516553, 0.23231, -0.0332655, 0.136382},
				{0.000997904, -0.00308778, -0.0085669, -0.0398553, -0.0676764, 0.0427083, -0.0442505, -0.00839938, -0.785091, -0.581033, 0.0906158, -0.0938964, -0.0539999, -0.054919, 0.0681528, 0.0246078, 0.0345532, 0.00472471, -0.00055096, 0.0806226},
				{-0.00218022, 0.00279713, -0.00290968, -0.0334259, 0.0516558, 0.0286052, -0.0401907, -0.139315, -0.110785, 0.107936, -0.546488, 0.496955, -0.305017, -0.147941, 0.402178, -0.0591877, 0.162537, 0.270898, -0.0322092, 0.154272},
				{-0.00133764, 0.000229975, -0.00187472, -0.00893384, -0.00315334, 0.00973097, -0.0571642, 0.00784611, 0.0677304, -0.103428, 0.242538, 0.115499, -0.213029, 0.0920581, -0.41694, -0.605324, 0.0114744, 0.537759, -0.0447413, 0.153623},
				{0.000384689, 0.00552973, -0.0190817, -0.00791713, -0.00503826, 0.0230003, 0.00467161, 0.047031, -0.0318906, 0.0541011, 0.0644698, 0.0789819, -0.0703329, -0.0478648, 0.0204103, 0.328747, -0.853804, 0.279287, -0.0632849, 0.236008},
				{-0.000382656, -0.00330682, 0.00451116, 0.283723, -0.517764, -0.140038, 0.136441, 0.445379, 0.283097, -0.216955, 0.100592, -0.16727, -0.212517, -0.198586, 0.338304, 0.0111164, 0.110702, 0.0977589, -0.0283591, 0.166433},
				{-0.530792, 0.172084, 0.264731, 0.114545, 0.0297489, 0.57794, 0.152119, 0.220981, 0.00263457, -0.00626427, -0.10756, 0.0990106, 0.0519334, -0.0550184, -0.203351, 0.00157132, 0.0431127, -0.187246, -0.0515285, 0.300832},
				{0.0532827, 0.220789, 0.128337, -0.151513, 0.0185447, -0.489308, -0.280329, 0.328266, -0.225011, 0.336721, 0.045687, 0.0865038, -0.0524376, -0.139683, -0.209145, 0.00273253, 0.0874016, -0.253611, -0.0618768, 0.409268},
				{0.000848159, 0.00339836, 0.010422, 0.0169595, -0.0159573, -0.0311845, 0.145325, 0.0276193, -0.0834262, 0.0933257, 0.334556, 0.335339, 0.734725, -0.054812, 0.340177, -0.151327, 0.0576609, 0.168814, -0.0326205, 0.148661},
				{-0.1543, -0.810079, -0.43363, 0.0277674, 0.0448509, -0.0105598, -0.012809, 0.138077, -0.0263643, 0.0359739, -0.0559595, 0.0689994, 0.0335786, -0.0422569, -0.136393, -0.000155212, 0.0351695, -0.140263, -0.0373819, 0.236854},
				{0.00222179, -0.0254324, -0.0392025, 0.105651, -0.287997, 0.196022, -0.00395851, -0.597945, -0.104467, 0.364877, 0.376021, -0.145109, -0.223783, -0.250163, 0.0728596, -0.000987171, 0.0827933, -0.116645, -0.0412334, 0.247184},
				{-0.000323136, -0.00565735, -0.0130242, -0.0482467, -0.018523, 0.0633412, 0.033075, 0.0726529, -0.0686566, 0.129258, 0.139988, -0.0164349, -0.19376, 0.866762, 0.30385, -0.00350248, 0.037318, -0.0892239, -0.0366688, 0.231517},
				{-0.00758161, -0.136494, 0.37732, 0.605879, 0.313381, -0.280707, -0.158115, -0.29974, 0.133924, -0.248357, -0.0704007, -0.0507129, 0.0772883, 0.0854372, 0.0216997, -0.000654789, -0.0266773, -0.0639069, -0.0426401, 0.269258},
				{0.0718053, 0.473296, -0.698853, 0.151858, 0.0533227, -0.0392623, 0.104996, -0.153321, 0.140562, -0.220647, -0.13059, 0.0434596, 0.0971395, 0.0452304, -0.112143, 0.00153106, 0.0100767, -0.139238, -0.0502747, 0.294958},
				{0.000135221, 0.0013218, -0.000966483, 3.02218e-05, -0.00287401, 0.00447414, 0.00370855, -0.000900252, 0.0088351, 0.00793118, -0.00321877, 0.00472083, 0.00179096, -0.00162219, 0.00112865, -0.0178065, -0.0266217, 0.0165513, 0.984466, 0.171172},
				{0.00114896, 0.00616192, -0.0109541, -0.311932, 0.664285, 0.0217568, 0.239098, 0.0796753, 0.160858, -0.103677, 0.283909, -0.215056, -0.225488, -0.244895, 0.282697, -0.00205536, 0.0900725, 0.0130293, -0.0278445, 0.184391},
				{0.826166, -0.105136, 0.164159, 0.0403252, 0.0018382, 0.383232, 0.12629, 0.134588, 0.0180125, -0.0244459, -0.0862168, 0.0722381, 0.0409233, -0.0312933, -0.141892, 0.00105413, 0.0261537, -0.125599, -0.035591, 0.206882}};

		this.freqs = new double[]{
				0.0692,
				0.0184,
				0.04,
				0.0186,
				0.0065,
				0.0238,
				0.0236,
				0.0557,
				0.0277,
				0.0905,
				0.1675,
				0.0221,
				0.0561,
				0.0611,
				0.0536,
				0.0725,
				0.087,
				0.0293,
				0.034,
				0.0428};


		init();
	}
}
