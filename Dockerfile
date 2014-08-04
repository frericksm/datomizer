FROM goodguide/base-oracle-java:8

# Set up Leiningen
ENV LEIN_ROOT ok
ENV LEIN_HOME /root/.lein
RUN mkdir $LEIN_HOME
ADD https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein /usr/bin/lein
RUN chmod +x /usr/bin/lein
# Init lein and ensure it's working before continuing
RUN lein version

RUN mkdir /build
WORKDIR /build

# Set up a copy or link to your ~/.lein/profiles.clj with your Datomic creds, in this tree, so Docker can install that in the image to allow downloading Datomic
ADD .lein_profiles.clj /root/.lein/profiles.clj

# Add project.clj and install deps
ADD project.clj /build/
RUN lein deps

# Then add the rest of the tree. (2-step approach helps avoid superflous deps building)
ADD . /build/
RUN lein do jar, install
