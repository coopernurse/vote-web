
docker-build:
	docker build -t coopernurse/voteweb .
	docker tag coopernurse/voteweb docker.io/coopernurse/voteweb

docker-push:
	docker push docker.io/coopernurse/voteweb

deploy:
	scp maelstrom.yml root@maelstromapp.com:/tmp
	ssh root@maelstromapp.com maelctl project put --file /tmp/maelstrom.yml
