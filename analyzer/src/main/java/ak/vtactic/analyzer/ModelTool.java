package ak.vtactic.analyzer;

import org.springframework.stereotype.Service;

import ak.vtactic.math.DiscreteProbDensity;

@Service
public class ModelTool {
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

}
