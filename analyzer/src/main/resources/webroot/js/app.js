angular.module('vtactic',[]).
	config(['$routeProvider', function($routeProvider) {
		$routeProvider.when('/plot/analyze/:plotUrl', {templateUrl: 'plot.html', controller: PlotCtrl})		
		.otherwise({redirectTo: '/plot/analyze/placement'});
}]);