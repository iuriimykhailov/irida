var datatable = (function(moment, tl, page) {
  'use strict';
  /**
   * Format the date in a table.
   * Requires the page to have the date format object.
   * @param date Date from server.
   * @returns {*}
   */
  function formatDate(date) {
    if (moment !== undefined && tl.date && tl.date.moment.short) {
      return moment(date).format(tl.date.moment.short);
    } else {
      return new Date(date);
    }
  }

  /**
   * Translate text from the server
   * @param data text to be translated
   * @returns {*}
   */
  function i18n(data) {
    if (page && page.lang && page.lang[data]) {
      return page.lang[data];
    } else {
      return data;
    }
  }

  function createItemButton(data, type, full) {
    if (tl && full.link && tl.BASE_URL) {
      return '<a class="item-link btn btn-default btn-xs" href="' + tl.BASE_URL + full.link + '">' + data + '</a>';
    } else {
      return data;
    }
  }

  /**
   * Called when the datatable is drawn.  This sizes the table container to either the height of the table, or, if the
   * table is larger than the screen height, it sets its bottom to near the bottom of the screen.
   */
  function tableDrawn() {
    var h = window.innerHeight,
      scrollBody = document.getElementsByClassName('dataTables_scrollBody')[0],
      scrollBodyClientRect = scrollBody.getBoundingClientRect(),
      table = scrollBody.getElementsByTagName('table')[0],
      tableClientRect = table.getBoundingClientRect();
    if (tableClientRect.bottom > h) {
      scrollBody.style.height = h - scrollBodyClientRect.top - 60 + 'px';
    } else {
      // + 1 to prevent the scrollbar from appearing
      scrollBody.style.height = tableClientRect.bottom - scrollBodyClientRect.top + 1 + 'px';
    }
  }

  window.onresize = tableDrawn;

  return {
    formatDate: formatDate,
    i18n: i18n,
    createItemButton: createItemButton,
    tableDrawn: tableDrawn
  };
})(window.moment, window.TL, window.PAGE);

