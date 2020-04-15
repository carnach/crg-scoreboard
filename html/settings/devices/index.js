$(function() {

  var deviceTable = $('.Devices');
  var buttons = $('.Buttons');

  $.each(['Comments', 'Writers', 'Active', 'Inactive'], function(_, i) {
    $('<label>').text(i).attr('for', 'Show'+i+'Button')
      .appendTo(buttons);
    $('<input type="checkbox">').attr('id', 'Show'+i+'Button')
      .appendTo(buttons).button()
      .on('change', function() {
        deviceTable.toggleClass('Show'+i, this.checked);
      });
  });
  $.each(['Comments', 'Writers', 'Active'], function(_, i) {
    buttons.children('#Show'+i+'Button').trigger('click');
  });


  function getDeviceTbody(id) {
    var tbody = deviceTable.children('[deviceId="'+ id +'"]');
    if (tbody.length == 0) {
      var prefix = 'ScoreBoard.Clients.Device(' + id + ')';
      var name = WS.state[prefix + '.Name'];
      tbody = $('<tbody>').attr('deviceId', id).attr('name', name)
        .append($('<tr>').append('<td class="Name" rowspan="1">')
          .append('<td class="Comment">')
          .append('<td class="Platform">')
          .append('<td class="RemoteAddr">')
          .append('<td class="LastSeenActive" rowspan="1">0s</td>')
          .append('<td class="LastSeenInactive" rowspan="1">')
          .append('<td class="LastWrite">')
          .append('<td class="Created">'));
      _windowFunctions.appendAlphaSortedByAttr(deviceTable, tbody, 'name', 1)

      tbody.find('td.Comment').append($('<input type="text">').on('change', function() {
        WS.Set(prefix + '.Comment', this.value);
      }));
    }
    return tbody;
  }

	WS.Register(['ScoreBoard.Clients.Device(*)'], function(k, v) {
    var id = k.Device;
    if (k.field == 'Client') {
      return;
    }
    if (v == null) {
      deviceTable.children('tbody[deviceId="'+ id +'"]').remove();
      return;
    }

    var tbody = getDeviceTbody(id);
    var tr = tbody.children().first();

    switch (k.field) {
      case 'Name':
        tr.children('td.Name').text(v);
        break;
      case 'RemoteAddr':
        tr.children('.RemoteAddr').text(v);
        break;
      case 'Platform':
        tr.children('.Platform').text(v);
        break;
      case 'Accessed':
        updateAge(tr.children('td.LastSeenInactive').attr('age', v).attr('title', new Date(v)));
        break;
      case 'Wrote':
        updateAge(tr.children('td.LastWrite').attr('age', v).attr('title', new Date(v)));
        tbody.toggleClass('HasWritten', v != 0);
        break;
      case 'Created':
        updateAge(tr.children('td.Created').attr('age', v).attr('title', new Date(v)));
        break;
      case 'Comment':
        tr.children('.Comment').children('input').val(v);
        tbody.toggleClass('HasComment', v != '');
        break;
    }
  });

	WS.Register(['ScoreBoard.Clients.Client(*)'], function(k, v) {
    var id = k.Client;
    if (v == null) {
      var tr = deviceTable.find('tr[clientId="'+ id +'"]');
      if (tr.siblings().length == 1) {
        tr.parent().removeClass('HasClients');
      }
      tr.siblings().first().children('[rowspan]').attr('rowspan', tr.siblings().length);
      tr.remove();
      return;
    }

    var tbody = getDeviceTbody(WS.state['ScoreBoard.Clients.Client(' + id + ').Device']);
    var tr = tbody.children('tr[clientId="'+ id +'"]');
    if (tr.length == 0) {
      var created = WS.state['ScoreBoard.Clients.Client(' + id + ').Created'];
      tr = $('<tr>').attr('clientId', id).attr('created', created)
        .append('<td class="Source">')
        .append('<td class="Platform">')
        .append('<td class="RemoteAddr">')
        .append('<td class="LastWrite">')
        .append('<td class="Created">');
        _windowFunctions.appendAlphaSortedByAttr(tbody, tr, 'created', 1);
        tr.siblings().first().children('[rowspan]').attr('rowspan', tr.siblings().length + 1);
        tbody.addClass('HasClients');
    }

    switch (k.field) {
      case 'Source':
        tr.children('.Source').text(v);
        break;
      case 'RemoteAddr':
        tr.children('.RemoteAddr').text(v);
        break;
      case 'Platform':
        tr.children('.Platform').text(v);
        break;
      case 'Wrote':
        updateAge(tr.children('td.LastWrite').attr('age', v).attr('title', new Date(v)));
        break;
      case 'Created':
        updateAge(tr.children('td.Created').attr('age', v).attr('title', new Date(v)));
        break;

    }
  });

  function updateAge(e, now) {
    if (!now) {
      now = new Date().getTime();
    }
    var age = e.attr('age');
    if (age == 0) {
      return;
    }
    var t = (now - age) / 1000;
    if (t < 60) {
      e.text(Math.floor(t) + 's');
    } else if (t < 3600) {
      e.text(Math.floor(t / 60) + 'm');
    } else if (t < 86400) {
      e.text(Math.floor(t / 3600) + 'h');
    } else {
      e.text(Math.floor(t / 86400) + 'd');
    }
  }

  setInterval(function(){
    var now = new Date().getTime()
    $.each(deviceTable.find('td[age]'), function(e) {
      updateAge($(this), now);
    });
  }, 1000);


  WS.AutoRegister();
  WS.Connect();
});

//# sourceURL=settings\devices\index.js
