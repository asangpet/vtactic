var customlayout = function(data) {
	var layout = {};
	var drag;
	var size = [1,1];
	var nodes = data.nodes;
	var links = data.links;
	var event = d3.dispatch("tick");
	var dragnode;
	var size;
	
	var findNodes = function(links,visited) {
		for (var i=0;i<links.length;i++) {
			if (links[i].used == 0 && $.inArray(links[i].source, visited) == -1) {
				return links[i].source;
			}
		}
		return -1;
	}
	
	layout.start = function() {
		var i, j, n, l;
		n = nodes.length;
		l = links.length;

		var finish = 0;
		for (i = 0; i < n; i++) {
			nodes[i].depth = -1;
		}
		for (i=0;i<l;i++) links[i].used=0;

		// find node level using BFS
		var q = [ 0 ];
		var visited = [];
		nodes[0].depth = 0;
		var maxdepth = 1;
		while (q.length > 0) {
			var head = q[0];
			q = q.slice(1);
			var depth = nodes[head].depth;
			if (depth == maxdepth) { maxdepth++; }
			for (j = 0; j < l; j++) {
				if (links[j].source == head && nodes[links[j].target].depth < 0) {
					links[j].used = 1;
					nodes[links[j].target].depth = depth + 1;
					if (($.inArray(links[j].target, visited) == -1) && ($.inArray(links[j].target,q) == -1)) {
						q.push(links[j].target);						
					}
				}
			}
			visited.push(head);
			if (q.length == 0) {
				head = findNodes(links,visited);
				if (head > 0) {
					q.push(head);
					nodes[head].dep=0;
				}
			}
		}

		for (i = 0; i < maxdepth; i++) {
			var height = 1;
			for (j = 0; j < n; j++) {
				if (nodes[j].depth == i) {
					nodes[j].vdepth = height++;
				}
			}
			for (j = 0; j < n; j++) {
				if (nodes[j].depth == i) {
					nodes[j].maxvdepth = height;
				}
			}
		}

		var boxwidth = size[0];
		var boxheight = size[1];
		var wportion = boxwidth / maxdepth;
		for (i = 0; i < n; i++) {
			nodes[i].x = (nodes[i].depth) * boxwidth / (maxdepth) + wportion / 2;
			if (nodes[i].maxvdepth == 0) {
				nodes[i].maxvdepth = 1;
			}
			if (nodes[i].x < 0) {
				nodes[i].x = Math.random() * boxwidth;
			}
			
			nodes[i].y = (nodes[i].vdepth) * boxheight / (nodes[i].maxvdepth);
			if (isNaN(nodes[i].y)) {
				nodes[i].y = Math.random() * boxheight;
			}
		}
		for (i = 0; i < l; i++) {
			links[i].source = nodes[links[i].source];
			links[i].target = nodes[links[i].target];
		}

		return layout;
	}
	
	layout.size = function(x) {
		if (!arguments.length) return size;
		size = x;
		return layout;
	}

	layout.drag = function() {
		if (!drag) {
			drag = d3.behavior.drag()
				.on("dragstart", function(d) { dragnode = d; })
				.on("drag", function(d) {
					if (dragnode) {
						dragnode.x += d3.event.dx;
						dragnode.y += d3.event.dy;
						event.tick({type : "tick"});
					}
				})
				.on("dragend", function(d) { dragnode = null; });
		}
		this.call(drag);
		//return layout;
	}

	layout.on = function(type, listener) {
		event[type].add(listener);
		return layout;
	}

	return d3.rebind(layout, event, "on");
	//return layout;
};

$(document).ready(
	function() {
		var w = 960, h = 500, fill = d3.scale.category20();
		var vis = d3.select("#gchart").append("svg:svg").attr("width", w).attr("height", h);
		//"http://" + window.location.hostname + ":8100/analyzer/graph",
		d3.json("http://" + window.location.hostname + ":8100/analyze/graph", function(error, graph) {
			if (error) return console.log("Error loading graph data " + error);
			/*
			 * var force = d3.layout.force()
			 * .charge(-200) .linkDistance(100)
			 * .friction(0.5) .nodes(json.nodes)
			 * .links(json.links) .size([w, h])
			 * .start();
			 */
			var mylayout = customlayout(graph).size([w, h]).start();
			//var mylayout = d3.layout.force().linkDistance(100).size([w,h]);
			//mylayout.nodes(graph.nodes).links(graph.links).start();

			var link = vis.selectAll(".link")
				.data(graph.links)
				.enter()
					.append("svg:line")
					.attr("stroke", "black")
					.attr("class", "link")
					.style("stroke-width",function(d) { return Math.sqrt(d.value); })
					.attr("x1", function(d) { return d.source.x; })
					.attr("y1", function(d) { return d.source.y; })
					.attr("x2", function(d) { return d.target.x; })
					.attr("y2", function(d) { return d.target.y; });

			//var maskdefs = vis.append("svg:defs");
			
			var node = vis.selectAll(".node")
				.data(graph.nodes).enter()
				.append("svg:g").attr("class", "node").call(mylayout.drag);

			/*
			maskdefs.selectAll(".mask").data(graph.nodes).enter()
				.append("svg:mask")
				.attr("class","mask")
				.attr("id",function(d) { return "imgmask_"+d.name; })
				.attr("maskUnits","objectBoundingBox")
				.attr("maskContentUnits","objectBoundingBox")
				.append("rect")
				.attr("x",0).attr("y",0).attr("width","1").attr("height","1")
				.attr("fill","red");
			*/
			
			node.append("svg:text")
				.style("fill", function(d) { return fill(d.color); })
				.style("text-anchor", "middle")
				.attr("x", function(d) { return d.x; })
				.attr("y", function(d) { return d.y - 8; })
				.text(function(d) { return d.name; })

			node.append("svg:title").text(function(d) { return d.name; });
			
			node.append("svg:image")
				.attr("x", function(d) { return d.x - 32; })
				.attr("y", function(d) { return d.y; })
				.attr("width", 64)
				.attr("height", 64)
				.attr("xlink:href","/images/osa_server.svg")
				.style("mask", function(d) { return "url(#imgmask_"+d.name+")"; });
			
			vis.style("opacity", 1e-6)
				.transition()
				.duration(500)
				.style("opacity", 1);

			mylayout.on("tick", function() { 
				link.attr("x1", function(d) { return d.source.x; })
					.attr("y1", function(d) { return d.source.y; })
					.attr("x2", function(d) { return d.target.x; })
					.attr("y2", function(d) { return d.target.y; });

				node.select("text")
					.attr("x", function(d) { return d.x; })
					.attr("y", function(d) { return d.y - 8; });
				node.select("image")
					.attr("x", function(d) { return d.x - 32; })
					.attr("y", function(d) { return d.y; });
			});
		});
	});
