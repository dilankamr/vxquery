<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
         pageEncoding="ISO-8859-1" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html ng-app="webUi">

<head>
    <meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
    <title>VXQuery Web UI</title>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/angular.js/1.4.8/angular.js"></script>
</head>

<body ng-controller="mainController as ctrl">
<form role="form" action="VXQueryWebUi/executeQuery">
    <div class="form-group">
        <label>Query:</label>
        <textarea type="text" name="query" class="form-control" id="pwd" ng-model="query"></textarea>
    </div>

    <button type="button" class="btn btn-default" ng-click="getResult()">Run</button>
</form>
<h4>Result:</h4>{{result}}
</body>

</html>


<!--    // this for temp-->
<script>
    var app = angular.module('webUi', []);

    app.controller("mainController", function ($scope, $http) {
        $scope.query = "doc('books.xml')/bookstore/book/title";
        $scope.result = "";

        $scope.getResult = function () { // Add new data to the DB
            if ($scope.query.length == 0) {
                alert("insert a query");
            } else {
                var data = {
                    query: $scope.query
                };
                console.log(data);
                var url = 'http://127.0.0.1:8080/VXQueryWebUi/executeQuery';
                $http
                        .post(url, $scope.query)
                        .then(
                        function (resp) {
                            $scope.result = resp.data
                        },
                        function (err) {
                            console.log(err);
                        });
            }
        };

    });
</script>