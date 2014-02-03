function xyplot(el, data) {
  var width = 1200;
  var height = 250;
  var margin = {top: 20, left: 33, bottom: 20, right: 20};
  var parseDate = d3.time.format("%m/%d/%Y %H:%M:%S").parse;

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

    data = data.map(function(d) {
      return { peakflow: parseInt(d.peakflow), timestamp: new Date(d.timestamp) };
    });

    data = data.sort(function(x,y) { return x.timestamp < y.timestamp;  });

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
    .attr('cy', function(d) { return y(d.peakflow); })
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
