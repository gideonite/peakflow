function table(el, data) {
  if (data === "")
      return;

  $el = $(el);
  $el.css('margin-top', '50px');
  $el.empty();

  // $el.attr("cellspacing", 10);

  // table header
  $header = $('<tr>');
  $th_peakflow = $('<th>').text("Peakflow");
  $th_date = $('<th>').text("Date");
  $th_time = $('<th>').text("Time");
  $header.append($th_date);
  $header.append($th_time);
  $header.append($th_peakflow);
  $el.append($header);

  // populate the table with data
  data.forEach(function(d,i) {
      var time = new Date(d.timestamp);
      var hours = time.getHours();
      var mins = time.getMinutes();

      $tr = $('<tr>');
      $tr.append($('<td>').text(time.toDateString()));
      $tr.append($('<td>').text(hours + ":" + mins));
      // $tr.append($('<td>').text(d.peakflow)).attr("align", "center");
      $tr.append($('<td>').text(d.peakflow));

      $el.append($tr);
  });
};
