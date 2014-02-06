function xyplot(el, data) {
  var width = 1200;
  var height = 250;
  var margin = {top: 20, left: 33, bottom: 20, right: 20};
  var parseDate = d3.time.format("%m/%d/%Y %H:%M:%S").parse;
  var five_minutes = 1000 * 60 * 5;
  var time_window = five_minutes;

  $el = d3.select(el);
  var svg = $el.append('svg');
  svg.attr('width', width+margin.left+margin.right);
  svg.attr('height', height+margin.top+margin.bottom);

  if (0 !== data.length) {
    draw();
  }

  function draw() {
    var main = svg.append('g');
    main.attr('transform', 'translate(' + margin.left + ',' + margin.top + ')')

    //
    // data munging
    //

    data = data.map(function(d) {
      return { peakflow: parseInt(d.peakflow), timestamp: new Date(d.timestamp) };
    });

    data = data.sort(function(x,y) { return x.timestamp < y.timestamp ? -1 : 1; });

    function first(l) { return l[0]; }
    function last(l) { return l[l.length-1]; }

    // group peakflows within a certain `time_window` of each other
    groups = data.slice(1).reduce(function(acc, curr) {
      var last_group = last(acc);

      if (curr.timestamp - first(last_group).timestamp < time_window) {
        last_group.push(curr);
      } else {
        acc.push([curr]);
      }

      return acc;
    }, [[data[0]]]);

    // calculate the average peakflow for each group
    groups = groups.map(function(group) {
      var total_peakflow = d3.sum(group.map(function(d) { return d.peakflow; }));
      var avg_peakflow = total_peakflow / group.length;

      return group.map(function(d) {
        d.avg_peakflow = avg_peakflow;
        return d;
      });
    });

    // flatten the list of groups into a list of data
    var tmp = [];
    groups.forEach(function(g) {
      g.forEach(function(d) {
        tmp.push(d);
      });
    });
    data = tmp;

    var x = d3.time.scale()
        .domain(d3.extent(data, function(d) { return d.timestamp; }))
        .range([0, width]);

    var y = d3.scale.linear()
        .domain(d3.extent(data, function(d) { return d.peakflow; }))
        .range([250, 0]);

    main.selectAll('circle').remove();

    main.selectAll('circle')
    .data(data)
    .enter()
    .append('circle')
    .attr('cx', function(d) { return x(d.timestamp); })
    .attr('cy', function(d) { return y(d.avg_peakflow); })
    .attr('r', 3)
    .attr('fill', 'blue');

    var xAxis = d3.svg.axis()
      .scale(x)
      .ticks(d3.time.week)
      .tickFormat(d3.time.format("%m/%d"))
      .orient('bottom');

    svg.append('g')
      .attr("transform", translate(margin.left, height+margin.top))
      .attr("class", "x-axis")
      .call(xAxis);

    var yAxis = d3.svg.axis()
      .scale(y)
      .orient('left')

    svg.append('g')
      .attr("class", "x-axis")
      .attr("transform", translate(margin.left, margin.top))
      .call(yAxis);
  }

  function translate(x,y) {
    return "translate(" + x + "," + y + ")";
  }

  return {
    append: function(datum) {
      svg.selectAll('g').remove();
      data.push(datum);
      draw();
    }
  }
}
