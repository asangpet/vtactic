package ak.vtactic.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ak.vtactic.math.DiscreteProbDensity;

@Service
public class ModelTool {
	private static final Logger log = LoggerFactory.getLogger(ModelTool.class);
	
	/**
	 * Find distribution of processing time, based on the non-contended processing time (aPdf)
	 * and the probability of having co-arrival request at a point in time t (dPdf) - approximate by inter-arrival
	 * 
	 * The co-arrival probability is calculated based on the mean interarrival (period)
	 * ideal processing time (processing) and rate of arrival within the period
	 * 
	 * @param rate
	 * @param processing
	 * @param period
	 * @param aPdf
	 * @param dPdf
	 * @return
	 */
	public DiscreteProbDensity findContendedProcessingTime(double rate, double processing, double period, DiscreteProbDensity aPdf, DiscreteProbDensity dPdf) {
		double lambda = 2*rate*processing / period;
		int degree = 5;
		DiscreteProbDensity[] nConv = new DiscreteProbDensity[degree];
		double[] coeff = new double[degree];
		coeff[0] = DiscreteProbDensity.poisson(0,lambda);
		nConv[0] = aPdf;
		double sum = coeff[0];						
		for (int i=1;i<degree;i++) {
			nConv[i] = DiscreteProbDensity.coConv(i+1,dPdf,aPdf).normalize();
			coeff[i] = DiscreteProbDensity.poisson(i,lambda);
			if (i<degree-1) {
				sum+=coeff[i];
			}
		}
		coeff[degree-1] = 1-sum;
		DiscreteProbDensity ndistP = DiscreteProbDensity.distribute(coeff, nConv);
		return ndistP;
	}
	
	public static DiscreteProbDensity findContendedProcessingIndependent(double arrivalPeriod, int arrivalCount, DiscreteProbDensity nonContendedProcessing) {
		//double lambda = arrivalCount / arrivalPeriod * (nonContendedProcessing.getPdf()[x]);
		//DiscreteProbDensity lagPdf = DiscreteProbDensity.expPdf(arrivalCount/arrivalPeriod);
		
		DiscreteProbDensity result = new DiscreteProbDensity(nonContendedProcessing);
		for (int x=0;x<result.getPdf().length;x++) {
			// x is the result processing time
			// consider the case where the non-contended processing time ranges from 0 to x
			double allK = 0;
			for (int k=0;k<x;k++) {
				double sum = 0;
				double lambda = arrivalCount / arrivalPeriod * k;
				for (int co = 0;co < 20;co++) {
					double coProb = DiscreteProbDensity.poisson(co, lambda);
					// should replace nonContendProc with the contended element processing time
					sum += nonContendedProcessing.getPdf()[x/(co+1)] * coProb;
				}
				allK += sum * nonContendedProcessing.getPdf()[k];
			}
			result.getPdf()[x] = allK;
			/*
			for (int k=0;k<x;k++) {
				sum += lagPdf.getPdf()[k] * nonContendedProcessing.getPdf()[(x+k)/(arrivalCount+1)];
			}
			*/
		}
		return result.normalize();
	}
	
	/**
	 * The contended processing time, consider A->B and A->C
	 * D = Tc-Tb (difference between arrivals of C, compared to B arrival),
	 * Pb = Processing time of B, Pc = Processing time of C
	 * X = Contended Processing time
	 * 
	 *     |--D--|
	 *     Tb    Tc       Tb+Pb  Tc+Pc
	 *     |     |          |      |
	 * |---|-----|----------|------|------> time
	 *     |-----|-Pb-------|      |
	 *           |--------Pc-------|
	 * 
	 * case 1: D >= 0, D >= Pb			   -> X = Pb
	 * case 2: D >= 0, D <  Pb, D >= Pb-Pc -> X = 2Pb - D	  or Pb + Pc - D
	 * case 3: D >= 0, D <  Pb, D <  Pb-Pc -> X = Pb + Pc
	 * case 4: D <  0, D < -Pc			   -> X = Pb
	 * case 5: D <  0, D >=-Pc, D >= Pb-Pc -> X = 2Pb         or Pb + Pc
	 * case 6: D <  0, D >=-Pc, D <  Pb-Pc -> X = D + Pb + Pc
	 * 
	 * lazy approx
	 * case Tc - Tb >= Pb or Tc - Tb <= -Pc --> X = Pb
	 * case otherwise --> X = Pb+Pc
	 * 
	 * case 1,4:
	 * for (b=0..Bmax) {
	 * 	for (d=b..inf) { // case 1
   	 *		X.pdf[b] += D.pdf[d] * Pb.pdf[b]
   	 *	}
	 * 	for (c=0..Bmax) { // case 4
	 * 		for (d=-inf..c) {
	 * 			X.pdf[b] += D.pdf[d] * Pc.pdf[c] * Pb.pdf[b]
	 * 		}
	 * 	}
	 * }
	 * 
	 * case 2,3:
	 * for (b=0..Bmax) {
	 * 	for (c=0..Cmax) {
	 * 		for (d=0..b-c) { // case 3
	 * 			X.pdf[b+c] += D.pdf[d] * Pb.pdf[b] * Pc.pdf[c]
	 * 		}
	 *		for (d=b-c..b) { // case 2
	 * 			X.pdf[b+b-d] += D.pdf[d] * Pb.pdf[b] * Pc.pdf[c]
	 *  	}
	 *  }
	 * }
	 * 
	 */
	public static DiscreteProbDensity contendedProcessing(
			DiscreteProbDensity TbPdf, DiscreteProbDensity TcPdf,
			DiscreteProbDensity PbPdf, DiscreteProbDensity PcPdf) {
		double[] Tb = TbPdf.getPdf(),
				Tc = TcPdf.getPdf(),
				Pb = PbPdf.getPdf(),
				Pc = PcPdf.getPdf();
	
		double[] x = new double[Tb.length];
		
		for (int tc = 0; tc < Tc.length; tc++) {
			if (Tc[tc] == 0) {
				log.info("Tc Prob {}",tc);
				continue;
			}
			for (int tb = 0; tb < Tb.length; tb++) {
				if (Tb[tb] == 0) continue;
				double tctb = Tc[tc] * Tb[tb];
				int d = tc - tb;
				for (int pb = 0; pb < Pb.length; pb++) {
					if (Pb[pb] == 0) continue;
					double pbtctb = Pb[pb] * tctb;
					for (int pc = 0; pc < Pc.length; pc++) {
						if (Pc[pc] == 0) { continue; }
/* D = TC-TB
	 * case 1: D >= 0, D >= Pb			   -> X = Pb
	 * case 2: D >= 0, D <  Pb, D >= Pb-Pc -> X = 2Pb - D	  or Pb + Pc - D
	 * case 3: D >= 0, D <  Pb, D <  Pb-Pc -> X = Pb + Pc
	 * case 4: D <  0, D < -Pc			   -> X = Pb
	 * case 5: D <  0, D >=-Pc, D >= Pb-Pc -> X = 2Pb         or Pb + Pc
	 * case 6: D <  0, D >=-Pc, D <  Pb-Pc -> X = D + Pb + Pc

 */
						if (d >= 0) {
							if (d >= pb) {
								x[pb] += Pc[pc] * pbtctb;
							} else {
								if (d >= pb-pc) {
									if (pb+pb-d < x.length) {
										x[pb+pb-d] += Pc[pc] * pbtctb;
									}
								} else {
									if (pb+pc < x.length) {
										x[pb+pc] += Pc[pc] * pbtctb;
									}
								}
							}
						} else {
							if (d < -pc) {
								x[pb] += Pc[pc] * pbtctb;
							} else {
								if (d >= pb-pc) {
									if (pb+pb < x.length) {
										x[pb+pb] += Pc[pc] * pbtctb;
									}
								} else {
									if (d+pb+pc < x.length){
										x[d+pb+pc] += Pc[pc] * pbtctb;
									}
								}
							}
						}
						/*
						if (tc-tb >= pb || tc-tb <= -pc) {
							x[pb] += Pc[pc] * pbtctb;
						} else {
							if (pb+pc < x.length) {
								x[pb+pc] += Pc[pc] * pbtctb;
							} else {
								break;
							}
						}*/
					}
				}
			}
		}
		
		DiscreteProbDensity xPdf = new DiscreteProbDensity(PbPdf);
		xPdf.setPdf(x);
		return xPdf;		
	}
	
	public static DiscreteProbDensity contendedProcessingSim(
			DiscreteProbDensity TbPdf, DiscreteProbDensity TcPdf,
			DiscreteProbDensity PbPdf, DiscreteProbDensity PcPdf,
			DiscreteProbDensity iPdf) {
		
		DiscreteProbDensity xPdf = new DiscreteProbDensity();

		int max = 1000000;
		int tb,tc,pb,pc,d,arrival;
		for (int i = 0; i < max; i++) {
			tb = TbPdf.random();
			tc = TcPdf.random();
			pb = PbPdf.random();
			pc = PcPdf.random();
			
			arrival = iPdf.random();
			if (arrival > pb) {
				xPdf.add(pb);
				continue;
			}
			
			d = tc-tb;
			if (d >= 0) {
				if (d >= pb) {
					xPdf.add(pb);
				} else {
					if (d >= pb-pc) {
						xPdf.add(pb+pc-d); // or 2pb - d
					} else {
						xPdf.add(pb+pc);
					}
				}
			} else {
				if (d < -pc) {
					xPdf.add(pb);
				} else {
					if (d >= pb-pc) {
						xPdf.add(pb+pc);  // or 2pb
					} else {
						xPdf.add(d+pb+pc);
					}
				}
			}
		}
		log.info("Total count {} {}",xPdf.count(),xPdf.getPdf()[xPdf.getPdf().length-1]);

		return xPdf.normalize();
	}	
}
