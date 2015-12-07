$(document).ready(function() {
	var table = $('#helptable').DataTable( {
		"sDom": 'flrtip',
		scrollX: 400,
		data: dataSet,
		columns: [
			{ title: "Term" },
			{ title: "File" },
			{ title: "Http Query" }
		],
		fixedHeader: {
			header: true
		}
	} );
	$(("<a class='toggle-vis' data-column='2'>Toggle Http Query</a>")).insertBefore('.dataTables_scrollHead');

	$('a.toggle-vis').on( 'click', function (e) {
		e.preventDefault();
		// Get the column API object
		var column = table.column( $(this).attr('data-column') );
		// Toggle the visibility
		column.visible( ! column.visible() );
	} );


} );