function PlotCtrl($scope, $routeParams, $http) {
  $scope.plotUrl = $routeParams.plotUrl;
  $scope.status = "Click 'plot' to load data";

  $scope.fetch = function() {
  	$scope.status = "Loading:"+$scope.plotUrl;
	// Load data asynchronously using jQuery. On success, add the data
    // to the options and initiate the chart
    var dataUrl = "analyze/"+$scope.plotUrl;
    var startTime = new Date();
    $http({method:'GET', url:dataUrl}).success(function(data,status,headers,config){
	  	$scope.status = status + " loaded in " + (new Date() - startTime) + "ms";
    	// define the plot options
        var pdfOptions = {
    		credits:{enabled:false},
            chart: {
                renderTo: 'pdfContainer',
                zoomType: 'xy',
            },
            legend: {
            	layout: 'vertical',
            	align: 'right',
            	verticalAlign: 'top',
            	y: 50
        	},
            title: {
                text: 'PDF Plot'
            },
    
            subtitle: {
                text: 'Source: '+$scope.plotUrl
            },
    
            xAxis: {            
                title: { text: 'Time(ms)' }
            },
    
            yAxis: {
                title: { text: 'pdf' },
            },
            plotOptions: {
            	line: {
            		lineWidth:1.0,
            		shadow:false,
            		marker:{enabled:false}
            	}
            },
    
            tooltip: {
                //shared: true,
                crosshairs: [true,true]
            }
        };
        
        var cdfOptions = angular.copy(pdfOptions);
        cdfOptions.chart.renderTo = 'cdfContainer';
        cdfOptions.title.text = 'CDF Plot';
        cdfOptions.yAxis.title.text = 'cdf';
    
    	var pdfChart = new Highcharts.Chart(pdfOptions);
    	var cdfChart = new Highcharts.Chart(cdfOptions);
    	angular.forEach(data, function(value, name) {
    		series = {id:name, name:name, data:value};
        	pdfChart.addSeries(series);
        	
        	var cdfValue = 0.0;
        	var cdfs = [];
        	for (var i=0;i<value.length;i++) {
        		cdfValue = cdfValue+value[i];
        		cdfs.push(cdfValue);
        	}
        	cseries = {id:name, name:name, data:cdfs};
        	cdfChart.addSeries(cseries);
    	});
    });
  	
  };
}